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
   [goog.history.EventType :as EventType]
) (:import goog.history.Html5History))

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

(defonce app-state (atom {:threads #{}}))

(defonce comment-ch (chan))

(defn app-component [app-state owner]
  (om/component
   (let [threads (app-state :threads)]
     (if (= 0 (count (app-state :threads)))
       (dom/div nil "Loading...")
       (om/build components/thread-component [0 threads] {:init-state {:expanded true}})
       ))))

(om/root app-component
         app-state
         {:shared {:comment-ch comment-ch}
          :target (js/document.getElementById "main")})

(defn form-pair [kvp]
  (string/join "=" (map js/encodeURIComponent kvp)))

(defn form-data [kvps]
  (string/join "&" (map form-pair kvps)))

(go-loop []
  (when-let [{:keys [text thread]} (<! comment-ch)]
    (let [data (form-data [["by" "anon"]
                           ["content" text]])
          path (str conf/http-base "/threads/" (thread :id))
          res (<! (POST path data))]
      (print res))
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
  (update-in state
             [:threads]
             #(-> %
                  (conj thread)
                  (update-set (parent-of-pred thread)
                              inc-child-count))))

(go-loop [ws (<! (connect! (str conf/ws-base "/")))]
  (when-let [value (<! (ws :in))]
    (swap! app-state add-thread value)
    (recur ws)))

(go (let [res (<! (GET (str conf/http-base "/threads")))]
  (swap! app-state assoc :threads (apply hash-set res))))
