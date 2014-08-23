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
                     (dom/h1 nil "Basilica is currently " (dom/em nil "invite only") ".")
                     (dom/p nil (str "Alright, realtalk. You can have an account. But I don't wanna write an interface to create them "
                                     "when only like five people will ever request one."))
                     (dom/p nil "Send me your email and preferred username and an account is yours.")
                     (dom/p nil (str "This does not mean that Basilica is stable, ready to use, or anything like that. "
                                     "Things don't work. Pretty critical features like notifications "
                                     "are just totally unimplemented."))
                     (dom/p nil (str "But if you wanna continue testing Betasilica, please sign up!"))
                     )

            (dom/a #js {:href (utils/site-url)} "return to a life you thought you understood")
            )))
