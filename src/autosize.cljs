(ns basilica.autosize
  (:require
   [clojure.string :as string]))

(defn camel-cased [s]
  (clojure.string/replace s #"-(\w)"
                          (comp clojure.string/upper-case second)))

(defn if-empty [text default]
  (if (= text "")
    default
    text))

(defn make-sizing-node []
  (let [node (js/document.createElement "div")]
    (doto (. node -style)
      (aset "visibility" "hidden")
      (aset "position" "absolute")
      (aset "top" "0"))
    node))

(defonce sizing-node (make-sizing-node))
(. js/document.body appendChild sizing-node)

(defn autosize [textarea]
  (let [source (js/getComputedStyle textarea)
        target-style (. sizing-node -style)]

    (doseq [property ["white-space" "word-wrap" "break-word"
                      "padding" "border" "box-sizing" "font"
                      "font-kerning" "line-height" "word-spacing"]]
      (aset target-style property (aget source (camel-cased property))))

    (aset target-style "width" (str (. textarea -clientWidth) "px"))

    (let [text (-> (. textarea -value)
                   (clojure.string/replace #"\n$" (constantly "\n "))
                   (if-empty "\n"))]
      (set! (.. sizing-node -textContent) text))

    (set! (.. textarea -style -height)
          (str (. sizing-node -clientHeight) "px"))))
