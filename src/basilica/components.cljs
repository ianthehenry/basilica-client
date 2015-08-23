(ns basilica.components
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

(defn sslify [url]
  (-> url
    string/trim
    (string/replace-first #"^\s*\w+://|^" "https://")))

(def renderer (new js/marked.Renderer))

(set! (.-image renderer) (fn [href title text]
  (this-as self (js/marked.Renderer.prototype.image.call self (sslify href) title text))))

(defn escape [str]
  (string/escape str {\< "&lt;"
                      \> "&gt;"
                      \& "&amp;"
                      \" "&quot;"
                      \' "&#39;"}))

(defn wraptag [tag inner]
  (str "<" tag ">" inner "</" tag ">"))

(set! (.-code renderer) (fn [code]
  (->> (string/split code #"\n")
    (map escape)
    (map (partial wraptag "code"))
    (string/join "")
    (wraptag "pre"))))

(. js/marked setOptions #js {:sanitize true
                             :renderer renderer})

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

(defn contains-origin-of-time [post-set]
  (some #(= 1 (% :id)) post-set))

(defn render-post-children [post-ch load-more parent app-state]
  (let [id-parent (:id parent)
        build-child (fn [{id-child :id}]
                      (om/build post-component
                                [id-child app-state]
                                {:react-key id-child
                                 :opts {:post-ch post-ch :load-more load-more}}))
        children (->> (parent :children)
                      (map (app-state :posts))
                      (sort-by :id >))
        has-more (and (nil? id-parent) (not (contains-origin-of-time children)))]
    (apply dom/div (classes "children")
           (if (app-state :token)
             (if (= (app-state :id-post-commenting) id-parent)
               (om/build add-post-component
                         (make-submit-handler post-ch id-parent))
               (dom/button (with-classes {:onClick (fn [e]
                                                     (om/update! app-state :id-post-commenting id-parent)
                                                     (.preventDefault e))}
                             "add-placeholder") "comment")))
           (lazy-cat
             (map build-child children)
             (if has-more
               [(dom/button (with-classes {:onClick load-more}
                              "load-more")
                  "Load more")])))))

(defn root-post-component [app-state owner {:keys [post-ch load-more]}]
  (om/component
   (render-post-children post-ch load-more (-> app-state :posts (get nil)) app-state)))

(defn count-post-children [db post]
  (let [children (map (db :posts) (post :children))]
    (reduce + 0 (->> children (map (fn [child]
      (inc (count-post-children db child))))))))

(defn post-component [[id-post app-state] owner {:keys [post-ch load-more]}]
  (reify
    om/IInitState
    (init-state [_] {:expanded-preference nil})
    om/IRenderState
    (render-state
     [_ {:keys [expanded-preference]}]
     (let [post ((app-state :posts) id-post)
           child-count (count-post-children app-state post)
           has-children (> child-count 0)
           implicitly-expanded (and (post :idParent) has-children)
           expanded (if (nil? expanded-preference)
                      implicitly-expanded
                      expanded-preference)
           is-authed (not (nil? (app-state :token)))]
       (dom/div (classes "post" (if expanded "expanded" "collapsed"))
                (dom/div (classes "this-post")
                         (dom/div (classes "gutter")
                                  (dom/div (classes "face")
                                           ; IMPLICIT COUPLING! If the CSS changes...
                                           (let [gravatar #(str "https://www.gravatar.com/avatar/"
                                                                (-> post :user :face :gravatar)
                                                                "?s=" % "&d=retro")]
                                             (dom/img #js {:srcSet (str (gravatar 24) " 1x"
                                                                        ","
                                                                        (gravatar 48) " 2x")})))
                                  (let [text (if expanded "-"
                                                          (if has-children
                                                            (str child-count)
                                                            (if is-authed "+" "0")))]
                                    (dom/button (with-classes {:onClick #(do
                                                                           (om/set-state! owner :expanded-preference (not expanded))
                                                                           (when (not expanded)
                                                                             (om/update! app-state :id-post-commenting id-post)))
                                                               :disabled (and (not has-children)
                                                                              (not is-authed))}
                                                  "toggle-button")
                                                text)))
                         (dom/div (classes "alley")
                                  (render-post-header post)
                                  (render-post-body post)))
                (if expanded
                  (render-post-children post-ch load-more post app-state)))
       ))))
