(ns basilica.utils
  (:require [basilica.conf :as conf]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [clojure.string :as string])
  (:import goog.history.Html5History))

(defn url [host path & components]
  (->> (concat [host] path components)
       (remove nil?)
       (string/join "/")))

(def api-url (partial url conf/api-host conf/api-path))
(def site-url (partial url conf/site-host conf/site-path))
(def ws-url (partial url conf/ws-host conf/ws-path))

(def site-hist-prefix
  (if (= conf/site-base [])
    "/"
    (string/join "/" (concat [""] conf/site-base [""]))))

(enable-console-print!)

(defn logger [area & msg]
  (apply print (str area) msg))

(defn with-classes [keys & all]
  (clj->js (into keys {:className (string/join " " all)})))

(defn classes [& all]
  (apply with-classes {} all))

(defn has-prefix [s prefix]
  (= (.indexOf s prefix) 0))

(def transformer (Html5History.TokenTransformer.))
(def hist (Html5History. js/window transformer))

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

(doto hist
  (.setUseFragment false)
  (.setPathPrefix (site-url)))

(events/listen hist EventType/NAVIGATE #(do (print (.-token %)) (secretary/dispatch! (.-token %))))

(defn navigate-to [path]
  (.setToken hist path))

(defn onclick [e]
  (when (instance? js/HTMLAnchorElement (.-target e))
    (let [path (.retrieveToken transformer
                               (.getPathPrefix hist)
                               (.-target e))]
      (when-not (or (nil? path) (nil? (secretary/locate-route path)))
        (.preventDefault e)
        (navigate-to path)))))

(defn start-history []
  (. hist setEnabled true)
  (events/listen js/document "click" onclick))
