(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [basilica.conf :as conf]
   [basilica.net :refer [GET connect! POST]]
   [basilica.components :as components]
   [clojure.set :refer [select]]
   [cljs.core.async :as async :refer [<!]]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import goog.history.Html5History))

(enable-console-print!)

(defn logger [area & msg]
  (apply print (str area) msg))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "*path" [path]
  (js/console.log (clj->js (path-from path))))

(let [h (Html5History.)]
  (events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h (.setUseFragment false)
    (.setPathPrefix "/basilica/")
    (.setEnabled true)))

(defonce app-state (atom {:posts #{}
                          :socket-state :disconnected
                          :loaded false}))

(defonce comment-ch (async/chan))
(defonce new-posts-ch (async/chan))

(defn app-component [app-state owner]
  (om/component
   (if (app-state :loaded)
     (dom/div nil
              (om/build components/header-component (app-state :socket-state))
              (om/build components/root-post-component
                        (app-state :posts)))
     (dom/div #js {:id "loading"}))))

(om/root app-component
         app-state
         {:shared {:comment-ch comment-ch}
          :target (js/document.getElementById "main")})

(defn form-pair [kvp]
  (string/join "=" (map js/encodeURIComponent kvp)))

(defn form-data [kvps]
  (string/join "&" (map form-pair kvps)))

(defn path-for [post]
  (apply str conf/api-base "/posts"
         (if (nil? post)
           []
           ["/" (post :id)])
         ))

(go-loop
 []
 (when-let [{:keys [text post]} (<! comment-ch)]
   (let [data (form-data [["by" "anon"]
                          ["content" text]])
         res (<! (POST (path-for post) data))]
     (when res
       (print "created post: " res)))
   (recur)))

(defn update-set [s pred f]
  (let [x (first (select pred s))]
    (-> s
        (disj x)
        (conj (f x)))))

(defn inc-child-count [post]
  (update-in post [:count] inc))

(defn parent-of-pred [post]
  #(= (% :id) (post :idParent)))

(defn add-post [state post]
  (let [update-parent (if (nil? (post :idParent))
                        identity
                        #(update-set % (parent-of-pred post) inc-child-count))]
    (update-in state
               [:posts]
               #(-> % (conj post) update-parent))))

(def log (partial logger :websockets))

(defn new-websocket []
  (log "trying to connect...")
  (connect! (str conf/ws-base "/")))

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

(go-loop
 []
 (let [post (<! new-posts-ch)]
   (swap! app-state add-post post)
   (when-not (js/document.hasFocus)
     (set-unread-stuff!)))
 (recur))

(go-loop
 [ws (<! (new-websocket))]
 (if ws
   (do
     (reset! reconnect-delay-ms min-reconnect)
     (log "connected")
     (swap! app-state assoc :socket-state :connected))
   (do
     (log "failed to connect")
     (swap! app-state assoc :socket-state :error)
     (recur (<! (reconnect-with-backoff)))))

 (loop []
   (when-let [value (<! (ws :in))]
     (async/put! new-posts-ch value)
     (recur)))

 (swap! app-state assoc :socket-state :disconnected)
 (log "disconnected")
 (recur (<! (reconnect-with-backoff))))

(go (when-let [res (<! (GET (str conf/api-base "/posts")))]
      (swap! app-state assoc :posts (apply hash-set res))
      (swap! app-state assoc :loaded true)))
