(ns basilica.login
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes]]
   [basilica.net :refer [POST]]
   [basilica.post-components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defn key-down [on-submit]
  (fn [e]
    (let [textarea (. e -target)
          key (. e -which)]
      (when (= key 13)
        (on-submit (. textarea -value))))))

(defn mouse-down [on-submit get-textarea]
  (fn [e]
    (let [textarea (get-textarea)]
      (on-submit (. textarea -value)))))

(defn request-code [email]
  (print email)
  #_ (POST (utils/api-url "codes") {:email email}))

(defn login-stuff [get-textarea]
  (let [on-submit request-code]
    [(dom/h1 nil "Well met, friend")
     (dom/p nil "What did you say your email address was?")
     (dom/div (classes "attached-button")
              (dom/input #js {:type "email"
                              :ref "input"
                              :onKeyDown (key-down on-submit)})
              (dom/button #js {:onClick (mouse-down on-submit get-textarea)}))]))

(defn root-component [app-state owner]
  (om/component
   (dom/div (classes "auth")
            (apply dom/div
                   (classes "message")
                   (login-stuff #(om/get-node owner "input")))
            (dom/a #js {:href (utils/site-url)} "give up on your hopes, dreams")
            )))
