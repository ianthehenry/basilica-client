(ns basilica.header
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes with-classes]]))

(def status-tooltips {:disconnected "reconnecting..."
                      :connected "you are one with the server"
                      :error "tell ian quick"})

(defn component [socket-state owner]
  (om/component
   (dom/div #js {:id "header"}
            (dom/h1 nil (dom/a #js {:href (utils/site-url)} "Basilica"))
            (dom/a (with-classes {:href (utils/site-url "login")}
                     "nav-link")
                   "log in")
            (dom/span #js {:dangerouslySetInnerHTML #js {:__html "&nbsp;-&nbsp;"}})
            (dom/a (with-classes {:href (utils/site-url "signup")}
                     "nav-link")
                   "get in on this")
            (dom/div (with-classes {; implicit coupling alert!
                                    ; allows the hover state to work on mobile safari
                                    :onTouchStart (fn [e])
                                    :id "socket-status"}
                       (name socket-state))
                     (dom/div (classes "tooltip")
                              (status-tooltips socket-state)))
            )))