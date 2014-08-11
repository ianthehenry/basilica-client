(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [basilica.net :refer [GET]]
   [basilica.components :as components]
   [cljs.core.async :as async :refer [chan close!]]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [goog.events :as events]
   [goog.history.EventType :as EventType]
) (:import goog.history.Html5History))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "*path" [path]
  (js/console.log (clj->js (path-from path))))

(let [h (Html5History.)]
  (events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h (.setUseFragment false)
          (.setPathPrefix "/basilica/")
          (.setEnabled true)))

(enable-console-print!)

(defonce app-state (atom {:root-thread {
  :id "basilica", :by "ian", :content "Basilica", :at "2014-08-11T01:16:44.421Z", :children [
    { :id "name-or-something", :by "ian", :content "This is a title", :at "2014-08-11T01:16:44.421Z", :children [
      { :id "a", :by "ian", :children [], :content "This is a comment", :at "2014-08-11T01:16:44.421Z" }
      { :id "b", :by "ian", :children [], :content "another comment?", :at "2014-08-11T01:16:44.421Z" }
      { :id "c", :by "ian", :children [], :content "I DISAGREE", :at "2014-08-11T01:16:44.421Z" }
    ]}
    { :id "haskell", :by "ian", :content "A safe place for haskell talk", :at "2014-08-11T01:16:44.421Z", :children [
      { :id "a", :by "hao", :children [], :content "A haskell thing", :at "2014-08-11T01:16:44.421Z" }
      { :id "b", :by "jack", :children [], :content "Monads??", :at "2014-08-11T01:16:44.421Z" }
      { :id "c", :by "cicero", :children [], :content "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas imperdiet nibh in odio eleifend ultricies. Mauris id eros condimentum, porttitor magna a, vulputate nulla. Morbi at semper urna. Sed venenatis nibh ut nisi consequat molestie. Vivamus tincidunt eu augue sit amet dictum. Pellentesque id porttitor ipsum. Quisque non blandit orci, sed pharetra erat. Nulla a iaculis orci, quis pretium urna. Nunc porttitor, magna vel auctor sagittis, libero eros condimentum magna, eget tincidunt est arcu ac elit. Donec in condimentum diam, vel tempor nulla. Vivamus rhoncus nibh felis, nec commodo odio gravida vitae. Proin tempus tortor ligula, sit amet blandit sapien cursus in.", :at "2014-08-11T01:16:44.421Z"}
      { :id "d", :by "ian", :children [], :content "Words about programming", :at "2014-08-11T01:16:44.421Z" }
    ]}
    { :id "new-story", :by "aaron", :content "what is this thing", :at "2014-08-11T01:26:03.436Z", :children [] }
]}}))

(om/root (partial components/thread-component nil true "")
         app-state
         {:path [:root-thread]
          :target (js/document.getElementById "main")})

#_ (go (let [res (<! (GET "http://localhost:3000/threads"))]
  (swap! app-state assoc :threads res)))
