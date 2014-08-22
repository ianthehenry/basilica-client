(ns basilica.core
  (:require
   [om.core :as om :include-macros true]
   [clojure.string :as string]
   [basilica.state :refer [app-state]]
   [basilica.utils :as utils]
   [basilica.posts :as posts]
   [basilica.signup :as signup]
   [basilica.login :as login]
   [secretary.core :as secretary :include-macros true :refer [defroute]]
   [cljs.core.async :as async]))

(defn path-from [s]
  (remove #(= "" %) (string/split s #"/")))

(defroute "/signup" []
  (swap! app-state assoc :route :signup))

(defroute "/login" [query-params]
  (swap! app-state assoc :route :login))

(defroute "*path" [path]
  (print "path: " (path-from path))
  (swap! app-state assoc :route :main))

(defn jsonify [m]
  (-> m clj->js js/JSON.stringify))

(defn persist [k v]
  (.setItem js/localStorage (name k) (jsonify v)))

(defmulti tx-listen (fn [{:keys [path]} _] path))
(defmethod tx-listen [:token] [{token :new-value} _]
  (persist :token token))
(defmethod tx-listen [:user] [{user :new-value} _]
  (persist :user user))
(defmethod tx-listen :default [_ _])

(defn root-component [app-state owner]
  (om/component
   (om/build
    ({:login login/root-component
      :signup signup/root-component
      :main posts/app-component} (app-state :route)) app-state)))

(utils/start-history)

(om/root root-component
         app-state
         {:target (js/document.getElementById "main")
          :tx-listen tx-listen})
