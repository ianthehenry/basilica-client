(ns basilica.net
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [goog.net.XhrIo :as xhr]
   [clojure.walk :refer [keywordize-keys]]
   [cljs.core.async :as async :refer [chan close! <! >! put!]]
   [goog.Uri :as uri]
   [clojure.string :as string]
   ))

(defn get-response [res]
  (if (= (.getResponseHeader res "Content-Type")
         "application/json")
    (-> res .getResponseJson js->clj keywordize-keys)
    (.getResponseText res)))

(defn callback [ch]
  (fn [event]
    (let [code (.. event -target getStatus)
          res (get-response (. event -target))]
      (go (>! ch [code res])
          (close! ch)))))

(defn request
  ([method url] (request method url nil))
  ([method url data]
   (let [ch (chan 1)]
     (xhr/send url (callback ch) method data)
     ch)))

(defn GET [path query]
  (let [uri (uri/parse path)]
    (doseq [[k v] query]
      (. uri setParameterValue (name k) v))
    (request "GET" uri)))

(defn form-pair [kvp]
  (string/join "=" (map js/encodeURIComponent kvp)))

(defn form-data [kvps]
  (string/join "&" (map form-pair kvps)))

(defn POST [url data]
  (request "POST" url
           (->> (seq data)
                (map (fn [[k v]] [(name k) v]))
                form-data)))

(defn connect! [uri]
  (let [on-connect (chan)]
    (let [from-server (chan) to-server (chan) ws (js/WebSocket. uri)]
      (doto ws
        (aset "onopen"
              (fn []
                (put! on-connect :success)
                (close! on-connect)
                (go-loop
                 []
                 (let [data (<! to-server)]
                   (if-not (nil? data)
                     (do (.send ws (-> data clj->js js/JSON.stringify))
                       (recur))
                     (do (close! from-server)
                       (.close ws)))))))
        (aset "onmessage"
              (fn [message]
                (when-let [data (-> message .-data js/JSON.parse js->clj keywordize-keys)]
                  (put! from-server data))))
        (aset "onerror"
              (fn [error]
                ; onclose is fired too,
                ; if it's open
                (put! on-connect error)
                (close! on-connect)))
        (aset "onclose"
              (fn []
                (close! from-server)
                (close! to-server))))
      (go (let [result (<! on-connect)]
            (if (= result :success)
              {:in from-server :out to-server}
              nil))
          ))))
