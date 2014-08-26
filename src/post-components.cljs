(ns basilica.post-components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes with-classes]]
   [basilica.autosize :refer [autosize]]
   [clojure.string :as string]
   [clojure.set :refer [select]]
   [cljs.core.async :as async :refer [chan close! put!]]))

(js/moment.locale "en" #js {:calendar #js {:lastDay "LT [yesterday]",
                                           :sameDay "LT"
                                           :nextDay "LT [tomorrow]"
                                           :lastWeek "LT [last] dddd"
                                           :nextWeek "LT [next] dddd"
                                           :sameElse "LT ddd, MMM D YYYY"
                                           }})

(defn format [timestamp-string]
  (.calendar (js/moment timestamp-string)))

(defn clear [textarea]
  (set! (. textarea -value) "")
  (autosize textarea))

(defn key-down [on-submit]
  (fn [e]
    (let [textarea (. e -target)
          key (. e -which)
          command (or (. e -metaKey) (. e -ctrlKey))]
      (when command
        (when (= key 13)
          (on-submit (. textarea -value))
          (clear textarea)))
      )))

(defn mouse-down [on-submit get-textarea]
  (fn [e]
    (let [textarea (get-textarea)]
      (on-submit (. textarea -value))
      (clear textarea)
      (.focus textarea))))

(defn add-post-component [on-submit owner]
  (reify
    om/IDidMount
    (did-mount [_]
               (let [textarea (om/get-node owner "input")
                     resize-listener #(autosize textarea)]
                 (.focus textarea)
                 (.addEventListener js/window "resize" resize-listener)
                 (om/set-state! owner :resize-listener resize-listener)))
    om/IWillUnmount
    (will-unmount [_] (.removeEventListener js/window (om/get-state owner :resize-listener)))
    om/IRender
    (render
     [_]
     (dom/div (classes "add-post" "attached-button")
              (dom/textarea #js {:placeholder "⌘↵ to submit"
                                 :ref "input"
                                 :onChange #(autosize (. % -target))
                                 :onKeyDown (key-down on-submit)})
              (dom/button #js {:onClick (mouse-down on-submit #(om/get-node owner "input"))}))
     )))

(defn render-post-header [post]
  (dom/div (classes "header")
           (dom/span (classes "by") (-> post :user :name))
           " "
           (dom/a #js {:href (utils/site-url (post :id))
                       :tabIndex -1}
                  (format (post :at)))))

(. js/marked setOptions #js {:sanitize true})

(defn markdown [text]
  (dom/div #js {:className "markdown"
                :dangerouslySetInnerHTML #js {:__html (js/marked text)}}))

(defn render-post-body [post]
  (dom/div (classes "content")
           (markdown (post :content))))

(declare post-component)

(defn make-submit-handler [post-ch id-parent]
  (fn [text]
    (when-not (= text "")
      (put! post-ch {:id-parent id-parent :text text}))))

(defn render-post-children [post-ch id-parent app-state]
  (let [all-posts (app-state :posts)
        build-child (fn [{id-child :id}]
                      (om/build post-component
                                [id-child app-state]
                                {:react-key id-child
                                 :opts {:post-ch post-ch}}))
        children (->> all-posts
                      (select #(= (% :idParent) id-parent))
                      (sort-by :id >))]
    (apply dom/div (classes "children")
           (if (app-state :token)
             (om/build add-post-component
                       (make-submit-handler post-ch id-parent)))
           (map build-child children))))

(defn root-post-component [app-state owner {:keys [post-ch]}]
  (om/component
   (render-post-children post-ch nil app-state)))

(defn post-component [[id-post app-state] owner {:keys [post-ch]}]
  (reify
    om/IInitState
    (init-state [_] {:expanded-preference nil})
    om/IRenderState
    (render-state
     [_ {:keys [expanded-preference]}]
     (let [all-posts (app-state :posts)
           post (->> all-posts
                     (select #(= (% :id) id-post))
                     first)
           child-count (post :count)
           has-children (> child-count 0)
           implicitly-expanded has-children
           expanded (if (nil? expanded-preference)
                      implicitly-expanded
                      expanded-preference)
           is-authed (not (nil? (app-state :token)))]
       (dom/div (classes "post" (if expanded "expanded" "collapsed"))
                (dom/div (classes "this-post")
                         (dom/div (classes "gutter")
                                  (dom/div (classes "face")
                                           ; IMPLICIT COUPLING! If the CSS changes...
                                           (dom/img #js {:src (str "https://www.gravatar.com/avatar/"
                                                                   (-> post :user :face :gravatar)
                                                                   "?s=24&d=retro")}))
                                  (let [text (if expanded "-"
                                                          (if has-children
                                                            (str child-count)
                                                            (if is-authed "+" "0")))]
                                    (dom/button (with-classes {:onClick #(om/set-state! owner :expanded-preference (not expanded))
                                                               :disabled (and (not has-children)
                                                                              (not is-authed))}
                                                  "toggle-button")
                                                text)))
                         (dom/div (classes "alley")
                                  (render-post-header post)
                                  (render-post-body post)))
                (if expanded
                  (render-post-children post-ch (post :id) app-state)))
       ))))
