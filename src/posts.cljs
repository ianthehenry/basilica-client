(ns basilica.posts
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils]
   [basilica.net :refer [GET connect! POST]]
   [basilica.post-components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defonce new-posts-ch (async/chan))

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

(defn wait [ms]
  (let [c (async/chan)]
    (js/setTimeout #(async/close! c) ms)
    c))

(def min-reconnect 1000)
(defonce reconnect-delay-ms (atom min-reconnect))
(defn next-backoff [current]
  (min (* 2 current) (* 1000 64)))

(defn reconnect-with-backoff []
  (log "reconnecting in" @reconnect-delay-ms "ms...")
  (let [c (async/chan)]
    (go
     (<! (wait @reconnect-delay-ms))
     (swap! reconnect-delay-ms next-backoff)
     (async/pipe (new-websocket) c))
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
    (om/update! :latest-post (-> res first :id))))

(defn load-data [cursor res]
  ((if (@cursor :loaded)
     load-more-data
     load-initial-data) cursor res))

(defn listen-to-sockets [load-data posts-request]
  (let [status-ch (async/chan)]
    (go-loop
     [ws (<! (new-websocket))]
     (if ws
       (do
         (reset! reconnect-delay-ms min-reconnect)
         (log "connected")
         (async/put! status-ch :connected))
       (do
         (log "failed to connect")
         (async/put! status-ch :error)
         (recur (<! (reconnect-with-backoff)))))

     (log "requesting data")
     (if-let [res (<! (posts-request))]
       (do
         (log "data load complete")
         (load-data res))
       (js/alert "a wild network inconsistency appears! please tell ian so he can fix the server"))

     (loop []
       (when-let [value (<! (ws :in))]
         (async/put! new-posts-ch value)
         (recur)))

     (async/put! status-ch :disconnected)
     (log "disconnected")
     (recur (<! (reconnect-with-backoff))))
    status-ch))

(defn keep-sockets-shiny [cursor]
  (let [status-ch (listen-to-sockets (partial load-data cursor)
                                     #(posts-request (@cursor :latest-post)))]
    (go-loop []
             (om/update! cursor :socket-state (<! status-ch))
             (recur))))

(defn absorb-incoming-posts [cursor]
  (go-loop
   []
   (let [post (<! new-posts-ch)]
     (om/transact! cursor :posts #(add-post % post))
     (om/transact! cursor :latest-post #(safe-max % (post :id)))
     (when-not (js/document.hasFocus)
       (set-unread-stuff!)))
   (recur)))

(defn upload-posts [post-ch]
  (go-loop
   []
   (when-let [{:keys [text post]} (<! post-ch)]
     (let [res (<! (POST (utils/api-url "posts" (:id post))
                         {:by "anon" :content text}))]
       (if res
         (print "created post: " res)
         (print "failed to create post!")))
     (recur))
   (print "post channel closed!")))

(defn app-component [app-state owner]
  (reify
    om/IWillMount
    (will-mount
     [_]
     ; TODO: clean all of this
     ; up on did-unmount

     (keep-sockets-shiny app-state)
     (absorb-incoming-posts app-state)
     (upload-posts (om/get-shared owner :post-ch)))
    om/IRender
    (render
     [_]
     (if (app-state :loaded)
       (dom/div nil
                (om/build components/header-component (app-state :socket-state))
                (om/build components/root-post-component
                          (app-state :posts)))
       (dom/div #js {:id "loading"
                     :className (name (app-state :socket-state))})))))
