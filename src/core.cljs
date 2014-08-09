(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.net :refer [GET]]
   [basilica.routes :as routes]
   [cljs.core.async :as async :refer [chan close!]]
   ))

(enable-console-print!)

(defonce app-state (atom {:root-thread {}}))

(defn thread-component-collapsed [on-click thread]
  (om/component
    (dom/div nil
      (dom/h2 nil (thread :content))
      (dom/button #js {:onClick #(on-click @thread)}
        "Expand"))
))

(defn thread-component-full [thread owner]
  (reify
    om/IInitState
    (init-state [_]
      {:expanded #{}})
    om/IRenderState
    (render-state [_ {:keys [expanded]}]
      (dom/div nil
        (dom/h1 nil (thread :content))
        (apply dom/div #js {:className "children"}
          (dom/textarea #js {:defaultValue "comment"})
          (->> (thread :children)
               (map (fn [child-thread]
                 (let [on-click (fn [thread]
                                  (js/console.log thread)
                                  (om/update-state! owner [:expanded] #(conj % thread)))
                       component (if (contains? expanded child-thread)
                                   thread-component-full
                                   (partial thread-component-collapsed on-click))]
                   (om/build component child-thread)))))
    )))))

(om/root thread-component-full
         app-state
         {:path [:root-thread]
          :target (js/document.getElementById "main")})

#_ (go (let [res (<! (GET "http://localhost:3000/threads"))]
  (swap! app-state assoc :threads res)))

(swap! app-state assoc :root-thread {
  :id "basilica", :content "Basilica", :children [
    { :id "name-or-something", :content "This is a title", :children [
      { :id "a", :children [], :content "This is a comment" }
      { :id "b", :children [], :content "another comment?" }
      { :id "c", :children [], :content "I DISAGREE" }
    ]}
    { :id "haskell", :content "A safe place for haskell talk", :children [
      { :id "a", :children [], :content "A haskell thing" }
      { :id "b", :children [], :content "Monads??" }
      { :id "c", :children [], :content "Words about programming" }
    ]}
]})
