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
  (POST (utils/api-url "codes") {:email email}))

(defn request-token [code]
  (POST (utils/api-url "tokens") {:code code}))

; states:
; - showing the email input
; - posting the email
; - email sent, awaiting code
; - have a code, requesting a token
; - got a token, redirecting to the homepage

(defn attached-button
  ([owner ref-name on-submit type] (attached-button owner ref-name on-submit type ""))
  ([owner ref-name on-submit type text]
   (dom/div (classes "attached-button")
            (dom/input #js {:type type
                            :ref ref-name
                            :defaultValue text
                            :onKeyDown (key-down on-submit)})
            (dom/button #js {:onClick (mouse-down on-submit
                                                  #(om/get-node owner ref-name))}))))

(defn awaiting-email [next owner]
  [(dom/h1 nil "Well met, friend")
   (dom/p nil "What did you say your email address was?")
   (attached-button owner "a" next "email")])

(defn sending-email [email owner]
  [(dom/h1 nil "Sending...")
   (dom/p nil "An email is on its way to " email)
   (dom/progress nil)])

(defn awaiting-code [email next owner]
  [(dom/h1 nil "Email sent")
   (dom/p nil "Probably. If " email " was a real account, anyway.")
   (dom/p nil "Enter the code below:")
   (attached-button owner "a" next "text")])

(defn requesting-token [owner]
  [(dom/h1 nil "Logging in...")
   (dom/p nil "Let's see if you belong here.")
   (dom/progress nil)])

(defn error [owner]
  [(dom/h1 nil "Error")
   (dom/p nil "Something went horribly wrong. Tell Ian.")])

(defn bad-code [email retry-code retry-token owner]
  [(dom/h1 nil "Bad code")
   (dom/p nil "We fed the code into the machine but no token came out.")
   (dom/p nil "Note that a code can only be used one time, and they expire after a few minutes.")
   (dom/p nil "You can re-enter it here:")
   (attached-button owner "a" retry-token "text")
   (dom/p nil "Or request a new code:")
   (attached-button owner "b" retry-code "email" email)])

(defn root-component [app-state owner {:keys [code]}]
  (letfn [(get-code [email]
                    (om/set-state! owner :email-address email)
                    (om/set-state! owner :state :sending-email)
                    (go (let [[status-code _] (<! (request-code email))]
                          (<! (async/timeout 1000))
                          (om/set-state! owner
                                         :state
                                         (if (= status-code 200)
                                           :awaiting-code
                                           :error))
                          )))
          (get-token [code]
                     (om/set-state! owner :state :requesting-token)
                     (go (let [[status-code token] (<! (request-token code))]
                           (<! (async/timeout 1000))
                           (cond
                            (= status-code 200)
                            (do
                              (print "logged in" token)
                              (om/update! app-state :token token)
                              (om/update! app-state :user (token :user))
                              (utils/navigate-to "/"))
                            (= status-code 401)
                            (do
                              (print "bad code")
                              (om/set-state! owner :state :bad-code))
                            :else (om/set-state owner :state :error))
                           )))
          (focus-input []
                       (let [node (om/get-node owner)
                             inputs (.getElementsByTagName node "input")
                             input (aget inputs 0)]
                         (when input (.focus input))))]
    (reify
      om/IInitState
      (init-state [_] {:state :awaiting-email
                       :email-address nil})
      om/IWillMount
      (will-mount
       [_]
       (when-not (nil? code)
         (get-token code)))
      om/IDidMount (did-mount [_] (focus-input))
      om/IDidUpdate (did-update [_ _ _] (focus-input))
      om/IRenderState
      (render-state
       [_ {:keys [state email-address]}]

       (let [f ({:awaiting-email (partial awaiting-email get-code)
                 :sending-email (partial sending-email email-address)
                 :awaiting-code (partial awaiting-code email-address get-token)
                 :requesting-token requesting-token
                 :bad-code (partial bad-code email-address get-code get-token)
                 :error error} state)]

         (dom/div (classes "auth")
                  (apply dom/div
                         (classes "message")
                         (f owner))
                  (dom/a #js {:href (utils/site-url)} "give up on your hopes, dreams")
                  ))))))
