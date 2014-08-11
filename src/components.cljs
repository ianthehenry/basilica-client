(ns basilica.components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [cljs.core.async :as async :refer [chan close!]]
))

(defn link-button [on-click cursor text]
  (dom/a #js {:href "#"
              :className "button"
              :onClick #(do (on-click @cursor) false)} text))

(defn format [timestamp-string]
  (.fromNow (js/moment timestamp-string)))

(defn classes [& all]
  (clj->js {:className (string/join " " all)}))

(defn bump-height [el delta]
  (let [height (.-clientHeight el)]
    (set! (.. el -style -height) (str (+ height delta) "px"))))

(defn comment-component [on-submit owner]
  (reify
    om/IInitState (init-state [_] {:text ""})
    om/IDidMount (did-mount [_] (.focus (om/get-node owner "input")))
    om/IRenderState (render-state [_ {:keys [text]}]
      (dom/div (classes "comment")
        "enter: newline, cmd-enter: submit, cmd-j/k: resize | markdown coming eventually"
        (dom/textarea #js {:value text
                           :ref "input"
                           :onChange #(om/set-state! owner :text (.. % -target -value))
                           :onKeyDown (fn [e]
                             (let [key (. e -which)
                                   command (or (. e -metaKey) (. e -ctrlKey))
                                   bump-amount 40]
                               (when command
                                 (when (= key 74) (bump-height (om/get-node owner "input") bump-amount))
                                 (when (= key 75) (bump-height (om/get-node owner "input") (- bump-amount)))
                                 (when (= key 13)
                                   (on-submit text)
                                   (om/set-state! owner :text "")))))
                          }))
)))

(defn thread-header-component [url thread]
  (om/component
    (dom/div (classes "header")
      (thread :by)
      " "
      (format (thread :at))
      " "
      (dom/a #js {:href url} "link")
)))

(defn thread-body-component [on-click thread]
  (om/component
    (dom/div (classes "content")
             (thread :content)
             " "
             (let [child-count (-> thread :children count)
                   text (if (= child-count 0) "comment" (str child-count " comments" ))]
               (link-button on-click thread text)))
))

(declare thread-component)

(defn thread-children-component [on-comment build-child threads]
  (om/component
    (apply dom/div (classes "children")
           (if on-comment (om/build comment-component on-comment))
           (map build-child threads))))

(defn thread-component [on-click expanded prefix thread owner]
  (reify
    om/IInitState (init-state [_] {:expanded-children #{}})
    om/IRenderState (render-state [_ {:keys [expanded-children]}]
      (let [show-toggle-button (or expanded (-> thread :children count (> 0)))
            my-url (string/join "/" [prefix (thread :id)])]
        (apply dom/div (classes "thread" (if expanded "expanded" "collapsed"))
          (om/build (partial thread-header-component my-url) thread)
          (om/build (partial thread-body-component on-click) thread)
          (if expanded [
            (om/build (partial thread-children-component print
             (fn [child-thread]
              (let [click (fn [op] (fn [thread] (om/update-state! owner :expanded-children #(op % thread))))
                    component (if (contains? expanded-children child-thread)
                                (partial thread-component (click disj) true my-url)
                                (partial thread-component (click conj) false my-url))]
                (om/build component child-thread))))
              (thread :children))])
)))))
