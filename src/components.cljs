(ns basilica.components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.conf :as conf]
   [clojure.string :as string]
   [clojure.set :refer [select]]
   [cljs.core.async :as async :refer [chan close! put!]]))

(defn link-button [on-click text]
  (dom/a #js {:href "#"
              :className "button"
              :onClick #(do (on-click) false)}
         text))

(defn format [timestamp-string]
  (.fromNow (js/moment timestamp-string)))

(defn with-classes [keys & all]
  (clj->js (into keys {:className (string/join " " all)})))

(defn classes [& all]
  (apply with-classes {} all))

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
    om/IDidMount
    (did-mount [_] (.focus (om/get-node owner "input")))
    om/IRender
    (render
     [_]
     (dom/div (classes "comment")
              "enter: newline, cmd-enter: submit, cmd-j/k: resize | markdown coming eventually"
              (dom/textarea #js {:ref "input", :onKeyDown (key-down on-submit)})))))

(defn render-thread-header [thread]
  (dom/div (classes "header")
           (thread :by)
           " "
           (format (thread :at))
           " "
           (dom/a #js {:href (str conf/site-base "/" (thread :id))} "link")))

(defn render-thread-body [on-click thread]
  (dom/div (classes "content")
           (thread :content)
           " "
           (let [child-count (thread :count)
                 text (if (= child-count 0) "comment" (str child-count " comments" ))]
             (link-button on-click text))))

(declare thread-component)

(defn render-thread-children [on-comment children all-threads]
  (let [build-child (fn [{id-child :id}]
                      (om/build thread-component
                                [id-child all-threads]
                                {:react-key id-child}))]
    (apply dom/div (classes "children")
           (om/build comment-component on-comment)
           (map build-child children))))

(defn root-thread-component [threads owner]
  (om/component
   (let [children (->> threads
                       (select (comp nil? :idParent))
                       (sort-by :id >))]
     (dom/div nil
              (dom/h1 nil "Basilica")
              (render-thread-children #(put! (om/get-shared owner :comment-ch) {:thread nil, :text %})
                                      children
                                      threads))
     )))

(defn thread-component [[id-thread threads] owner]
  (reify
    om/IInitState
    (init-state [_] {:expanded false})
    om/IRenderState
    (render-state
     [_ {:keys [expanded]}]
     (let [thread (->> threads
                       (select #(= (% :id) id-thread))
                       first)
           children (->> threads
                         (select #(= (% :idParent) id-thread))
                         (sort-by :id >))]
       (dom/div (classes "thread" (if expanded "expanded" "collapsed"))
                (render-thread-header thread)
                (render-thread-body #(om/update-state! owner :expanded not) thread)
                (if expanded
                  (render-thread-children #(put! (om/get-shared owner :comment-ch) {:thread thread, :text %})
                                          children
                                          threads)))
     ))))
