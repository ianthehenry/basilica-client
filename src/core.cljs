(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
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

(defonce app-state (atom {:threads #{
  { :id 0, :count 3, :by "ian", :content "Basilica", :at "2014-08-11T01:16:44.421Z"}
  { :id 1, :idParent 0, :count 3, :by "ian", :content "This is a title", :at "2014-08-11T01:16:44.421Z" }
  { :id 2, :idParent 1, :count 0, :by "ian", :content "This is a comment", :at "2014-08-11T01:16:44.421Z" }
  { :id 3, :idParent 1, :count 0, :by "ian", :content "another comment?", :at "2014-08-11T01:16:44.421Z" }
  { :id 4, :idParent 1, :count 0, :by "ian", :content "I DISAGREE", :at "2014-08-11T01:16:44.421Z" }
  { :id 5, :idParent 0, :count 4, :by "ian", :content "A safe place for haskell talk", :at "2014-08-11T01:16:44.421Z" }
  { :id 6, :idParent 5, :count 0, :by "hao", :content "A haskell thing", :at "2014-08-11T01:16:44.421Z" }
  { :id 7, :idParent 5, :count 0, :by "jack", :content "Monads??", :at "2014-08-11T01:16:44.421Z" }
  { :id 8, :idParent 5, :count 0, :by "cicero", :content "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas imperdiet nibh in odio eleifend ultricies. Mauris id eros condimentum, porttitor magna a, vulputate nulla. Morbi at semper urna. Sed venenatis nibh ut nisi consequat molestie. Vivamus tincidunt eu augue sit amet dictum. Pellentesque id porttitor ipsum. Quisque non blandit orci, sed pharetra erat. Nulla a iaculis orci, quis pretium urna. Nunc porttitor, magna vel auctor sagittis, libero eros condimentum magna, eget tincidunt est arcu ac elit. Donec in condimentum diam, vel tempor nulla. Vivamus rhoncus nibh felis, nec commodo odio gravida vitae. Proin tempus tortor ligula, sit amet blandit sapien cursus in.", :at "2014-08-11T01:16:44.421Z"}
  { :id 9, :idParent 5, :count 0, :by "ian", :content "Words about programming", :at "2014-08-11T01:16:44.421Z" }
  { :id 10, :idParent 0, :count 0, :by "aaron", :content "what is this thing", :at "2014-08-11T01:26:03.436Z" }
}}))

(defonce comment-ch (chan))

(om/root (partial components/thread-component comment-ch print true 0)
         app-state
         {:path [:threads]
          :target (js/document.getElementById "main")})

(defn form-data [kvps]
  (string/join "&" (map (partial string/join "=") kvps)))

(go-loop []
  (when-let [{:keys [text thread]} (<! comment-ch)]
    (let [data (form-data [["by" "ian"]
                           ["content" text]])
          path (str "http://localhost:3000/threads/" (thread :id))
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

(go-loop [ws (<! (connect! "ws://localhost:3000"))]
  (when-let [value (<! (ws :in))]
    (swap! app-state add-thread value)
    (recur ws)))

#_ (go (let [res (<! (GET "http://localhost:3000/threads"))]
  (swap! app-state assoc :threads res)))
