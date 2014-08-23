(ns basilica.posts
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes with-classes]]
   [basilica.header :as header]
   [basilica.net :refer [GET connect! POST]]
   [basilica.post-components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defn update-set [s pred f]
  (let [x (first (select pred s))]
    (-> s
        (disj x)
        (conj (f x)))))

(defn inc-child-count [post]
  (update-in post [:count] inc))

(defn parent-of-pred [post]
  #(= (% :id) (post :idParent)))

(defn add-post [current-posts post]
  (let [update-parent (if (nil? (post :idParent))
                        identity
                        #(update-set % (parent-of-pred post) inc-child-count))]
    (-> current-posts (conj post) update-parent)))

(def log (partial utils/logger :websockets))

(defn new-websocket []
  (log "trying to connect...")
  (connect! (utils/ws-url)))

(def min-reconnect 1000)
(defonce reconnect-delay-ms (atom min-reconnect))
(defn next-backoff [current]
  (min (* 2 current) (* 1000 64)))

(defn reconnect-with-backoff [canceled?]
  (log "reconnecting in" @reconnect-delay-ms "ms...")
  (let [c (async/chan)]
    (go
     (<! (async/timeout @reconnect-delay-ms))
     (swap! reconnect-delay-ms next-backoff)
     (if (canceled?)
       (do
         (log "not reconnecting; canceled")
         (async/close! c))
       (async/pipe (new-websocket) c)))
    c))

(defn get-title []
  (aget (js/document.getElementsByTagName "title") 0))

(defn set-unread-stuff! []
  (aset (get-title) "textContent" "*Basilica"))

(defn clear-unread-stuff! []
  (aset (get-title) "textContent" "Basilica"))

(aset js/window "onfocus" clear-unread-stuff!)

(defn safe-max [& args]
  (let [filtered (filter (complement nil?) args)]
    (if (seq filtered)
      (apply max filtered)
      nil)))

(defn posts-request [latest]
  (GET (utils/api-url "posts")
       (if (nil? latest) nil {:after latest})))

(defn load-initial-data [cursor res]
  (doto cursor
    (om/update! :posts (apply hash-set res))
    (om/update! :latest-post (-> res first :id))
    (om/update! :loaded true)))

(defn load-more-data [cursor res]
  (doto cursor
    (om/transact! :posts #(union % (apply hash-set res)))
    (om/transact! :latest-post #(-> res first :id (safe-max %)))))

(defn load-data [cursor res]
  ((if (@cursor :loaded)
     load-more-data
     load-initial-data) cursor res))

(defn safe-close! [ch]
  (when-not (nil? ch)
    (async/close! ch)))

(defn listen-to-sockets [load-data posts-request]
  (let [status-ch (async/chan)
        delta-ch (async/chan)
        canceled-atom (atom false)
        ws-out-ch (atom nil)
        cancel-fn (fn []
                    (log "canceling")
                    (reset! canceled-atom true)
                    (async/close! delta-ch)
                    (async/close! status-ch)
                    (safe-close! @ws-out-ch))
        reconnect-maybe (fn []
                          (if-not @canceled-atom
                            (reconnect-with-backoff #(deref canceled-atom))
                            (doto (async/chan) (async/close!))))]
    (go-loop
     [ws (<! (new-websocket))]
     (if @canceled-atom
       (safe-close! @ws-out-ch)
       (do
         (if ws
           (do
             (reset! reconnect-delay-ms min-reconnect)
             (log "connected")
             (async/put! status-ch :connected)
             (reset! ws-out-ch (ws :out)))
           (do
             (log "failed to connect")
             (async/put! status-ch :error)
             (reset! ws-out-ch nil)
             (recur (<! (reconnect-maybe)))))

         (log "requesting data")
         (let [[code res] (<! (posts-request))]
           (if (= code 200)
             (do
               (log "data load complete")
               (load-data res))
             (do
               (log "failed to load data")
               (js/alert "a wild network inconsistency appears! please tell ian so he can fix the server"))))

         (loop []
           (when-let [value (<! (ws :in))]
             (async/put! delta-ch value)
             (recur)))

         (async/put! status-ch :disconnected)
         (log "disconnected")
         (recur (<! (reconnect-maybe)))))
     (log "canceled"))
    [status-ch delta-ch cancel-fn]))

(defn keep-sockets-shiny [cursor status-ch]
  (go-loop []
           (when-let [status (<! status-ch)]
             (om/update! cursor :socket-state status)
             (recur))))

(defn absorb-incoming-posts [cursor delta-ch]
  (go-loop
   []
   (when-let [post (<! delta-ch)]
     (om/transact! cursor :posts #(add-post % post))
     (om/transact! cursor :latest-post #(safe-max % (post :id)))
     (when-not (js/document.hasFocus)
       (set-unread-stuff!))
     (recur))))

(defn upload-posts [post-ch get-username]
  (go-loop
   []
   (when-let [{:keys [text post]} (<! post-ch)]
     (let [[code res] (<! (POST (utils/api-url "posts" (:id post))
                                {:by (get-username) :content text}))]
       (if (= code 200)
         (print "created post: " res)
         (print "failed to create post with code:" code "response:" res)))
     (recur))))

(defn app-component [app-state owner]
  (reify
    om/IInitState
    (init-state [_] {:on-stop identity})
    om/IDidMount
    (did-mount
     [_]
     (let [[status-ch delta-ch stop-fn]
           (listen-to-sockets (partial load-data app-state)
                              #(posts-request (@app-state :latest-post)))
           post-ch (async/chan)]
       (keep-sockets-shiny app-state status-ch)
       (absorb-incoming-posts app-state delta-ch)
       (om/set-state! owner :on-stop stop-fn)
       (om/set-state! owner :post-ch post-ch)
       (upload-posts post-ch #(-> @app-state :user :name))))
    om/IWillUnmount
    (will-unmount
     [_]
     ((om/get-state owner :on-stop))
     (async/close! (om/get-state owner :post-ch)))
    om/IRender
    (render
     [_]
     (if (app-state :loaded)
       (dom/div nil
                (om/build header/component (app-state :socket-state))
                (om/build components/root-post-component
                          (app-state :posts)
                          {:opts {:post-ch (om/get-state owner :post-ch)}}))
       (dom/div (with-classes {:id "loading"}
                  (name (app-state :socket-state))))))))
