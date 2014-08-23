(ns basilica.header
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.net :refer [DELETE]]
   [basilica.utils :as utils :refer [classes with-classes]]
   [cljs.core.async :as async :refer [<!]]))

(def status-tooltips {:disconnected "reconnecting..."
                      :connected "you are one with the server"
                      :error "tell ian quick"})

(defn signed-out []
  [(dom/a #js {:href (utils/site-url "login")}
          "log in")
   (dom/span #js {:dangerouslySetInnerHTML #js {:__html "&nbsp;-&nbsp;"}})
   (dom/a #js {:href (utils/site-url "signup")}
          "sign up")])

(defn signed-in [app-state]
  [(dom/span nil "signed in as " (-> app-state :user :name))
   (dom/span #js {:dangerouslySetInnerHTML #js {:__html "&nbsp;-&nbsp;"}})
   (dom/a #js {:href "#"
               :onClick (fn [e]
                          (doto e
                            (.preventDefault)
                            (.stopPropagation))
                          ; TODO: implement this part. this part is kinda important
                          ; (go (<! (DELETE (utils/api-url "tokens" (-> @app-state :token :id)))))
                          (om/update! app-state :user nil)
                          (om/update! app-state :token nil))}
          "log out")])

(defn component [app-state owner]
  (om/component
   (dom/div #js {:id "header"}
            (dom/h1 nil (dom/a #js {:href (utils/site-url)} "Basilica"))
            (apply dom/div
                   (classes "nav-links")
                   (if (and (app-state :token) (app-state :user))
                     (signed-in app-state)
                     (signed-out)))
            (dom/div (with-classes {; implicit coupling alert!
                                    ; allows the hover state to work on mobile safari
                                    :onTouchStart (fn [e])
                                    :id "socket-status"}
                       (name (app-state :socket-state))
                       (dom/div (classes "tooltip")
                                (status-tooltips (app-state :socket-state)))
                       )))))