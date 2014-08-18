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

(defonce app-state (atom {:posts #{}
                          :loaded false}))

(defonce comment-ch (chan))

(defn app-component [app-state owner]
  (om/component
   (if (app-state :loaded)
     (om/build components/root-post-component
               (app-state :posts))
     (dom/div nil "Loading..."))))

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

(go-loop [ws (<! (connect! (str conf/ws-base "/")))]
         (when-let [value (<! (ws :in))]
           (swap! app-state add-post value)
           (recur ws)))

(go (when-let [res (<! (GET (str conf/api-base "/posts")))]
      (swap! app-state assoc :posts (apply hash-set res))
      (swap! app-state assoc :loaded true)))
