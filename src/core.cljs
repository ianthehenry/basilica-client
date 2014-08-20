(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [basilica.utils :as utils]
   [basilica.posts :as posts]
   [basilica.signup :as signup]
   [cljs.core.async :as async :refer [<!]]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [goog.Uri :as uri]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import goog.history.Html5History))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "/signup" [path]
  (om/root signup/root-component
           nil
           {:target (js/document.getElementById "main")}))

(let [post-ch (async/chan)
      app-state (atom {:posts #{}
                       :latest-post nil
                       :loaded false
                       :socket-state :disconnected})]
  (defroute "*path" [path]
    (print "path: " (path-from path))
    (om/root posts/app-component
             app-state
             {:shared {:post-ch post-ch}
              :target (js/document.getElementById "main")})))

(def transformer (Html5History.TokenTransformer.))
(aset transformer "createUrl" (fn [path prefix location] path))
(aset transformer "retrieveToken" (fn [prefix location] (.substr (. location -href) (count prefix))))

(def hist (Html5History. js/window transformer))
(events/listen hist EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
(doto hist
  (.setUseFragment false)
  (.setPathPrefix (utils/site-url))
  (.setEnabled true))

(defn onclick [e]
  (when (instance? js/HTMLAnchorElement (.-target e))
    (let [path (.getPath (uri/parse (.-href (.-target e))))]
      (when-not (nil? (secretary/locate-route path))
        (.preventDefault e)
        (.setToken hist path)))))

(events/listen js/document "click" onclick)
