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
    (dom/div #js {:className "thread collapsed"}
      (dom/button #js {:onClick #(on-click @thread)
                       :className "toggle"}
        "+")
      (dom/div #js {:className "content"} (thread :content))
)))

(defn thread-component-expanded [on-click thread owner]
  (reify
    om/IInitState (init-state [_]
      {:expanded #{}})
    om/IRenderState (render-state [_ {:keys [expanded]}]
      (dom/div #js {:className "thread expanded"}

        (if on-click
          (dom/button #js {:onClick #(on-click @thread)
                           :className "toggle"}
            "-"))

        (dom/div #js {:className "content"} (thread :content))
        (apply dom/div #js {:className "children"}
          (dom/textarea #js {:defaultValue "comment"})
          (->> (thread :children)
               (map (fn [child-thread]
                 (let [expand   (fn [thread]
                                  (om/update-state! owner [:expanded] #(conj % thread)))
                       collapse (fn [thread]
                                  (om/update-state! owner [:expanded] #(disj % thread)))
                       component (if (contains? expanded child-thread)
                                   (partial thread-component-expanded collapse)
                                   (partial thread-component-collapsed expand))]
                   (om/build component child-thread)))))
    )))))

(om/root (partial thread-component-expanded nil)
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
