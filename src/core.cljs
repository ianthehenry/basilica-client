(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [basilica.utils :as utils]
   [basilica.posts :as posts]
   [basilica.signup :as signup]
   [basilica.login :as login]
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

(defroute "/login" [path]
  (om/root login/root-component
           nil
           {:target (js/document.getElementById "main")}))

(let [app-state (atom {:posts #{}
                       :latest-post nil
                       :username "anon"
                       :loaded false
                       :socket-state :disconnected})]

  (defroute "*path" [path]
    (print "path: " (path-from path))
    (om/root posts/app-component
             app-state
             {:shared {:post-ch (async/chan)}
              :target (js/document.getElementById "main")})))

(defn has-prefix [s prefix]
  (= (.indexOf s prefix) 0))

(def transformer (Html5History.TokenTransformer.))
(set! (. transformer -createUrl)
      (fn [path prefix location]
        (str prefix "/" (.replace path #"^/" (constantly "")))))
(set! (. transformer -retrieveToken)
      (fn [prefix location]
        (let [path (. location -href)]
          (if (has-prefix path prefix)
            (.substr path
                     (count prefix))
            nil))))

(def hist (Html5History. js/window transformer))
(events/listen hist EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
(doto hist
  (.setUseFragment false)
  (.setPathPrefix (utils/site-url))
  (.setEnabled true))

(defn onclick [e]
  (when (instance? js/HTMLAnchorElement (.-target e))
    (let [path (.retrieveToken transformer
                               (.getPathPrefix hist)
                               (.-target e))]
      (when-not (or (nil? path) (nil? (secretary/locate-route path)))
        (.preventDefault e)
        (.setToken hist path)))))

(events/listen js/document "click" onclick)
