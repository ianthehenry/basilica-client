(ns basilica.utils
  (:require [basilica.conf :as conf]
            [clojure.string :as string]))

(defn url [host path & components]
  (->> (concat [host] path components)
       (remove nil?)
       (string/join "/")))

(def api-url (partial url conf/api-host conf/api-path))
(def site-url (partial url conf/site-host conf/site-path))
(def ws-url (partial url conf/ws-host conf/ws-path))
