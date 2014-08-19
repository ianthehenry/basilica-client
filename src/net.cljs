(ns basilica.net
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [goog.net.XhrIo :as xhr]
   [clojure.walk :refer [keywordize-keys]]
   [cljs.core.async :as async :refer [chan close! <! >! put!]]
   ))

(defn callback [ch]
  (fn [event]
    (let [code (-> event .-target .getStatus)]
      (if (= code 200)
        (let [res (-> event .-target .getResponseJson js->clj keywordize-keys)]
          (go (>! ch res)
              (close! ch)))
        (do
          (print "Error!")
          (close! ch))))))

(defn request
  ([method url] (request method url nil))
  ([method url data]
   (let [ch (chan 1)]
     (xhr/send url (callback ch) method data)
     ch)))

(def GET (partial request "GET"))
(def POST (partial request "POST"))

; adapted from https://github.com/loganlinn/cljs-websockets-async
(defn connect!
  "Connects to a websocket. Returns a channel that, when connected, puts a
  map with with keys,
  :uri  The URI connected to
  :ws   Raw Websocket object
  :in   Channel to write values to socket on
  :out  Channel to recieve socket data on"
  ([uri] (connect! uri {}))
  ([uri {:keys [in out] :or {in chan out chan}}]
   (let [on-connect (chan)]
     (let [in (in) out (out) ws (js/WebSocket. uri)]
       (doto ws
         (aset "onopen" (fn []
           (put! on-connect :success)
           (close! on-connect)
           (go-loop []
             (let [data (<! in)]
               (if-not (nil? data)
                 (do (.send ws (-> data clj->js js/JSON.stringify))
                     (recur))
                 (do (close! out)
                     (.close ws)))))))
         (aset "onmessage" (fn [message]
           (when-let [data (-> message .-data js/JSON.parse js->clj keywordize-keys)]
             (put! out data))))
         (aset "onerror" (fn [error]
           (put! on-connect error)
           (close! on-connect)))
         (aset "onclose" (fn []
           (close! in)
           (close! out))))
       (go (let [result (<! on-connect)]
             (if (= result :success)
               {:uri uri :ws ws :out in :in out}
               nil))
           )))))
