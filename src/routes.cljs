(ns basilica.routes
  (:require [secretary.core :as secretary :include-macros true :refer [defroute]]))

(defroute "/threads/:id" {:as params}
  (js/console.log (str "Thread: " (:id params))))

(secretary/dispatch! "/threads/test-thread")
