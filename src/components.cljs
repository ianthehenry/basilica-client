(ns basilica.components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [cljs.core.async :as async :refer [chan close! put!]]
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

(defn key-down [on-submit]
  (fn [e]
    (let [node (. e -target)
          key (. e -which)
          command (or (. e -metaKey) (. e -ctrlKey))
          bump-amount 40]
    (when command
      (when (= key 74) (bump-height node bump-amount))
      (when (= key 75) (bump-height node (- bump-amount)))
      (when (= key 13)
        (on-submit (. node -value))
        (set! (. node -value) ""))))))

(defn comment-component [on-submit owner]
  (reify
    om/IDidMount (did-mount [_] (.focus (om/get-node owner "input")))
    om/IRender (render [_]
      (dom/div (classes "comment")
        "enter: newline, cmd-enter: submit, cmd-j/k: resize | markdown coming eventually"
        (dom/textarea #js {:ref "input", :onKeyDown (key-down on-submit)})))))

(defn thread-header-component [thread]
  (om/component
    (dom/div (classes "header")
      (thread :by)
      " "
      (format (thread :at))
      " "
      (dom/a #js {:href (string/join "/" (thread :id))} "link")
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

(defn thread-component [comment-ch on-click expanded thread owner]
  (reify
    om/IInitState (init-state [_] {:expanded-children #{}})
    om/IRenderState (render-state [_ {:keys [expanded-children]}]
      (let [show-toggle-button (or expanded (-> thread :children count (> 0)))]
        (apply dom/div (classes "thread" (if expanded "expanded" "collapsed"))
          (om/build thread-header-component thread)
          (om/build (partial thread-body-component on-click) thread)
          (if expanded [
            (om/build (partial thread-children-component #(put! comment-ch {:thread @thread, :text %})
             (fn [child-thread]
              (let [click (fn [op] (fn [thread] (om/update-state! owner :expanded-children #(op % thread))))
                    component (if (contains? expanded-children child-thread)
                                (partial thread-component comment-ch (click disj) true)
                                (partial thread-component comment-ch (click conj) false))]
                (om/build component child-thread))))
              (thread :children))])
)))))
