(ns basilica.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [basilica.net :refer [GET]]
   [basilica.routes :as routes]
   [cljs.core.async :as async :refer [chan close!]]
))

(enable-console-print!)

(defonce app-state (atom {:root-thread {}}))

(defn toggle-button [on-click cursor]
  (if on-click
    (dom/button #js {:onClick #(on-click @cursor)
                     :className "toggle"})))

(defn classes [& all]
  (clj->js {:className (string/join " " all)}))

(defn comment-component [on-submit owner]
  (reify
    om/IInitState (init-state [_] {:text ""})
    om/IDidMount (did-mount [_] (.focus (om/get-node owner)))
    om/IRenderState (render-state [_ {:keys [text]}]
      (dom/textarea #js {:value text
                         :placeholder "comment..."
                         :onChange #(om/set-state! owner :text (.. % -target -value))
                         :onKeyDown (fn [e]
                           (when (and (= (. e -which) 13)
                                      (. e -metaKey))
                             (on-submit text)
                             (om/set-state! owner :text "")))
                        }
))))

(defn thread-component [on-click expanded thread owner]
  (reify
    om/IInitState (init-state [_] {:expanded-children #{}})
    om/IRenderState (render-state [_ {:keys [expanded-children]}]
      (dom/div (classes "thread" (if expanded "expanded" "collapsed"))
        (if (or expanded (-> thread :children count (> 0)))
          (toggle-button on-click thread))

        (let [reply-button (if expanded
                             []
                             [" " (dom/a #js {:href "#"
                                              :className "reply-button"
                                              :onClick #(on-click @thread)} "reply")])]
          (apply dom/div (classes "content")
                         (thread :content)
                         reply-button))

        (if expanded
          (apply dom/div (classes "children")
            (om/build comment-component print)
            (->> (thread :children)
                 (map (fn [child-thread]
                   (let [click (fn [op] (fn [thread] (om/update-state! owner :expanded-children #(op % thread))))
                         component (if (contains? expanded-children child-thread)
                                     (partial thread-component (click disj) true)
                                     (partial thread-component (click conj) false))]
                     (om/build component child-thread)))))))
))))

(om/root (partial thread-component nil true)
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
      { :id "c", :children [], :content "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas imperdiet nibh in odio eleifend ultricies. Mauris id eros condimentum, porttitor magna a, vulputate nulla. Morbi at semper urna. Sed venenatis nibh ut nisi consequat molestie. Vivamus tincidunt eu augue sit amet dictum. Pellentesque id porttitor ipsum. Quisque non blandit orci, sed pharetra erat. Nulla a iaculis orci, quis pretium urna. Nunc porttitor, magna vel auctor sagittis, libero eros condimentum magna, eget tincidunt est arcu ac elit. Donec in condimentum diam, vel tempor nulla. Vivamus rhoncus nibh felis, nec commodo odio gravida vitae. Proin tempus tortor ligula, sit amet blandit sapien cursus in."}
      { :id "d", :children [], :content "Words about programming" }
    ]}
]})
