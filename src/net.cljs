(ns basilica.net
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [goog.net.XhrIo :as xhr]
   [cljs.core.async :as async :refer [chan close!]]
   ))

(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url (fn [event]
      (let [code (-> event .-target .getStatus)]
        (if (= code 200)
          (let [res (-> event .-target .getResponseJson js->clj)]
                      (go (>! ch res)
                          (close! ch)))
          (print "Error!")))
      ))
    ch))
