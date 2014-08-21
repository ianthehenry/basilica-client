(ns basilica.core
  (:require
   [om.core :as om :include-macros true]
   [clojure.string :as string]
   [basilica.utils :as utils]
   [basilica.posts :as posts]
   [basilica.signup :as signup]
   [basilica.login :as login]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [cljs.core.async :as async]))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "/signup" [path]
  (print "signup!")
  (om/root signup/root-component
           nil
           {:target (js/document.getElementById "main")}))

(defroute "/login" [path]
  (print "login!")
  (om/root login/root-component
           nil
           {:target (js/document.getElementById "main")}))

(let [app-state (atom {:posts #{}
                       :latest-post nil
                       :username "anon"
                       :loaded false
                       :socket-state :disconnected})]

  (defroute "*path" [path]
    (print "path: " (path-from path))
    (om/root posts/app-component
             app-state
             {:shared {:post-ch (async/chan)}
              :target (js/document.getElementById "main")})))

(utils/start-history)
