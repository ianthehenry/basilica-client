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

(defn attached-button [owner on-submit type]
  (dom/div (classes "attached-button")
           (dom/input #js {:type type
                           :ref "input"
                           :onKeyDown (key-down on-submit)})
           (dom/button #js {:onClick (mouse-down on-submit
                                                 #(om/get-node owner "input"))})))

(defn awaiting-email [next owner]
  [(dom/h1 nil "Well met, friend")
   (dom/p nil "What did you say your email address was?")
   (attached-button owner next "email")])

(defn sending-email [email owner]
  [(dom/h1 nil "Sending...")
   (dom/p nil "An email is on its way to " email)
   (dom/progress nil)])

(defn awaiting-code [email next owner]
  [(dom/h1 nil "Email sent")
   (dom/p nil "Probably. If " email " was a real account, anyway.")
   (dom/p nil "Enter the code below:")
   (attached-button owner next "text")])

(defn requesting-token [owner]
  [(dom/h1 nil "Logging in...")
   (dom/p nil "Let's see if you belong here.")
   (dom/progress nil)])

(def states {:awaiting-email awaiting-email
             :sending-email sending-email
             :awaiting-code awaiting-code
             :requesting-token requesting-token
             })

(defn root-component [app-state owner]
  (reify
    om/IInitState
    (init-state [_] {:state :awaiting-email
                     :email-address nil})
    om/IRenderState
    (render-state
     [_ {:keys [state email-address]}]

     (let [get-email (fn [email]
                       (om/set-state! owner :email-address email)
                       (om/set-state! owner :state :sending-email)
                       (go (let [[status-code _] (<! (request-code email))]
                             ; TODO: check status code
                             (<! (async/timeout 1000))
                             (om/set-state! owner :state :awaiting-code)
                             )))
           get-token (fn [code]
                       (om/set-state! owner :state :requesting-token)
                       (go (let [[status-code token] (<! (request-token code))]
                             ; TODO: check status code
                             (<! (async/timeout 1000))
                             (print "logged in" token)
                             (utils/navigate-to "/")
                             )))
           f ({:awaiting-email (partial awaiting-email get-email)
               :sending-email (partial sending-email email-address)
               :awaiting-code (partial awaiting-code email-address get-token)
               :requesting-token requesting-token} state)]

       (dom/div (classes "auth")
                (apply dom/div
                       (classes "message")
                       (f owner))
                (dom/a #js {:href (utils/site-url)} "give up on your hopes, dreams")
                )))))
