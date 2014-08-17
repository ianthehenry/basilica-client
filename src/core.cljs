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
   [cljs.core.async :as async :refer [chan close!]]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import goog.history.Html5History))

(enable-console-print!)

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "*path" [path]
  (js/console.log (clj->js (path-from path))))

(let [h (Html5History.)]
  (events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h (.setUseFragment false)
    (.setPathPrefix "/basilica/")
    (.setEnabled true)))

(defonce app-state (atom {:threads #{}
                          :loaded false}))

(defonce comment-ch (chan))

(defn app-component [app-state owner]
  (om/component
   (if (app-state :loaded)
     (om/build components/root-thread-component
               (app-state :threads))
     (dom/div nil "Loading..."))))

(om/root app-component
         app-state
         {:shared {:comment-ch comment-ch}
          :target (js/document.getElementById "main")})

(defn form-pair [kvp]
  (string/join "=" (map js/encodeURIComponent kvp)))

(defn form-data [kvps]
  (string/join "&" (map form-pair kvps)))

(defn path-for [thread]
  (apply str conf/api-base "/threads"
         (if (nil? thread)
           []
           ["/" (thread :id)])
         ))

(go-loop
 []
 (when-let [{:keys [text thread]} (<! comment-ch)]
   (let [data (form-data [["by" "anon"]
                          ["content" text]])
         res (<! (POST (path-for thread) data))]
     (when res
       (print "created thread: " res)))
   (recur)))

(defn update-set [s pred f]
  (let [x (first (select pred s))]
    (-> s
        (disj x)
        (conj (f x)))))

(defn inc-child-count [thread]
  (update-in thread [:count] inc))

(defn parent-of-pred [thread]
  #(= (% :id) (thread :idParent)))

(defn add-thread [state thread]
  (let [update-parent (if (nil? (thread :idParent))
                        identity
                        #(update-set % (parent-of-pred thread) inc-child-count))]
    (update-in state
               [:threads]
               #(-> % (conj thread) update-parent))))

(go-loop [ws (<! (connect! (str conf/ws-base "/")))]
         (when-let [value (<! (ws :in))]
           (swap! app-state add-thread value)
           (recur ws)))

(go (when-let [res (<! (GET (str conf/api-base "/threads")))]
      (swap! app-state assoc :threads (apply hash-set res))
      (swap! app-state assoc :loaded true)))
