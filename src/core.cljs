(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [clojure.string :as string]
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [goog.net.XhrIo :as xhr]
   [cljs.core.async :as async :refer [chan close!]]
   ))

(enable-console-print!)

(defonce app-state (atom {:threads []}))

(defn thread-component [thread]
  (om/component
    (dom/li nil (get thread "title"))))

(defn app-component [app-state]
  (apply dom/ul nil (map (partial om/build thread-component) (app-state :threads))))

(om/root app-component
         app-state
         {:target (js/document.getElementById "main")})

(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url (fn [event]
                    (let [res (-> event .-target .getResponseJson js->clj)]
                      (go (>! ch res)
                          (close! ch)))))
    ch))

(go (let [res (<! (GET "http://localhost:3000/threads"))]
  (swap! app-state assoc :threads res)))
