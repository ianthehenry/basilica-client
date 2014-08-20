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
   (dom/div #js {:id "signup"}
            (dom/div (classes "message")
                     (dom/h1 nil
                             "Basilica is currently "
                             (dom/em nil "invite only")
                             ".")
                     (dom/p nil
                            "This is partly to give an air of exclusivity, but mostly because there is no concept of accounts, "
                            "logging in, signing up, invites, or anything else yet. This page only exists so I can test the router.")
                     (dom/p nil
                            "Fortunately for you, you can interact with all aspects of basilica in anonymous mode!"))

            (dom/a #js {:href (utils/site-url)} "return to a life you thought you understood")
            )))
