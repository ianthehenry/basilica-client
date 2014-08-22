(ns basilica.state
  (:require
   [clojure.walk :refer [keywordize-keys]]))

(defn unjsonify [string]
  (-> string js/JSON.parse js->clj keywordize-keys))

(let [token-entry (.getItem js/localStorage "token")
      user-entry (.getItem js/localStorage "user")
      initial-token (if (nil? token-entry) nil (unjsonify token-entry))
      initial-user (if (nil? user-entry) nil (unjsonify user-entry))]

  (def app-state (atom {:posts #{}
                        :users (if (nil? initial-user) #{} #{initial-user})
                        :latest-post nil
                        :username "anon"
                        :loaded false
                        :token initial-token
                        :socket-state :disconnected})))
