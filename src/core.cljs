(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [clojure.string :as string]
   [basilica.utils :as utils]
   [basilica.posts :as posts]
   [cljs.core.async :as async :refer [<!]]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import goog.history.Html5History))

(let [hist (Html5History.)]
  (events/listen hist EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto hist
    (.setUseFragment false)
    (.setPathPrefix utils/site-hist-prefix)
    (.setEnabled true)))

(defroute "/signup" [path]
  (print "sign up!"))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "*path" [path]
  (print "path: " (path-from path)))

(defonce app-state (atom {:posts #{}
                          :latest-post nil
                          :loaded false
                          :socket-state :disconnected}))

(om/root posts/app-component
         app-state
         {:shared {:comment-ch (async/chan)}
          :target (js/document.getElementById "main")})
