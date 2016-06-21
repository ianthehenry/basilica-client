(ns basilica.signup
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes]]
   [basilica.net :refer [GET connect! POST]]
   [basilica.components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defn key-down [on-submit]
  (fn [e]
    (let [key (. e -which)]
      (when (= key 13)
        (on-submit)))))

(defn awaiting-input [next error email username owner]
  (letfn [(submit [e] (apply next (->> ["email" "username"]
                                       (map #(om/get-node owner %))
                                       (map #(. % -value)))))]
    [(dom/h1 nil "Hello, stranger")
     (if error
       (dom/p (classes "error") error)
       (dom/p nil "I've got a brand new Basilaccount here with your name on it."))
     (dom/p nil "Email address:")
     (dom/input #js {:type "email"
                     :ref "email"
                     :onKeyDown (key-down submit)
                     :defaultValue email})
     (dom/p nil "Username:")
     (dom/input #js {:type "text"
                     :ref "username"
                     :onKeyDown (key-down submit)
                     :defaultValue username})
     (dom/p nil "Submit button:")
     (dom/button #js {:onClick submit} "Submit")]))

(defn loading [owner]
  [(dom/h1 nil "Hang on...")
   (dom/p nil "Let's see if you belong here.")
   (dom/progress nil)])

(defn account-created [email owner]
  [(dom/h1 nil "It is done")
   (dom/p nil "There is no going back. We've sent an email to " email " with a link you can use to log in.")])

(defn request-user [email username]
  (POST (utils/api-url "users") {:email email :name username}))

(defn root-component [app-state owner]
  (letfn [(get-user [email username]
                    (om/update-state! owner #(conj % {:email email
                                                      :username username
                                                      :state :loading}))
                    (go (let [[status-code response] (<! (request-user email username))]
                          (if (= status-code 200)
                            (om/set-state! owner :state :account-created)
                            (om/update-state! owner #(conj % {:state :awaiting-input
                                                              :error (case status-code
                                                                       409 "username or email already taken"
                                                                       400 "invalid username"
                                                                       "unknown error")})))
                          )))
          (focus-input []
                       (let [node (om/get-node owner)
                             inputs (.getElementsByTagName node "input")
                             input (aget inputs 0)]
                         (when input (.focus input))))]
    (reify
      om/IInitState
      (init-state [_] {:state :awaiting-input
                       :email ""
                       :username ""
                       :error nil})

      om/IDidMount (did-mount [_] (focus-input))
      om/IDidUpdate (did-update [_ _ _] (focus-input))
      om/IRenderState
      (render-state
       [_ {:keys [state error username email]}]

       (let [f ({:awaiting-input (partial awaiting-input get-user error email username)
                 :loading loading
                 :account-created (partial account-created email)
                 } state)]

         (dom/div (classes "auth signup")
                  (apply dom/div
                         (classes "message")
                         (f owner))
                  (dom/a #js {:href (utils/site-url)} "return to the life you thought you understood")
                  ))))))
