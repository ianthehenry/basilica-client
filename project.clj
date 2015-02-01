(defproject basilica-client "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2740"]
                 [org.omcljs/om "0.8.7"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [secretary "1.2.0"]
                 ]
  :plugins [[lein-cljsbuild "1.0.4"]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src" "conf/dev"]
                        :compiler {:main basilica.core
                                   :output-to "out/dev/main.js"
                                   :output-dir "out/dev"
                                   :asset-path "http://localhost:3333"
                                   :optimizations :none
                                   :source-map "out/dev/main.js.map"}}
                       {:id "prod"
                        :source-paths ["src" "conf/prod"]
                        :compiler {:output-to "out/prod/main.js"
                                   :output-dir "out/prod"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :output-wrapper false
                                   :preamble ["momentjs/min/moment.min.js" "marked/lib/marked.js"]
                                   :externs ["momentjs/moment.js" "marked/lib/marked.js"]
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]})
