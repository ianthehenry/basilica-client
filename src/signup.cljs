(ns basilica.signup
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.utils :as utils]
   [basilica.net :refer [GET connect! POST]]
   [basilica.post-components :as components]
   [clojure.set :refer [select union]]
   [cljs.core.async :as async :refer [<!]]))

(defn root-component [app-state owner]
  (om/component
   (dom/div nil
            "signup"
            (dom/a #js {:href (utils/site-url)} "go back")
            )))
