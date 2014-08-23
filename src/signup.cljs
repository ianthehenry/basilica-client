(ns basilica.signup
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils :refer [classes]]
   [basilica.net :refer [GET connect! POST]]
   [basilica.post-components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defn root-component [app-state owner]
  (om/component
   (dom/div (classes "auth")
            (dom/div (classes "message")
                     (dom/h1 nil
                             "Basilica is currently "
                             (dom/em nil "invite only")
                             ".")
                     (dom/p nil (str
                            "This is partly to give an air of exclusivity, but mostly because I don't want to bother "
                            "writing an interface for creating accounts when it's only gonna happen like five times."))
                     (dom/p nil
                            "If you ask Ian for an account he'll give you one. Just gimme your preferred email address and username."))

            (dom/a #js {:href (utils/site-url)} "return to a life you thought you understood")
            )))
