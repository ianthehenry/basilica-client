(defproject basilica-client "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.40"]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/core.async "0.2.374"]
                 [secretary "1.2.3"]
                 ]
  :plugins [[lein-cljsbuild "1.1.3"]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src" "conf/dev"]
                        :compiler {:main basilica.core
                                   :output-to "out/dev/main.js"
                                   :output-dir "out/dev"
                                   :asset-path "http://localhost:3333"
                                   :optimizations :none
                                   :source-map true}}
                       {:id "prod"
                        :source-paths ["src" "conf/prod"]
                        :compiler {:output-to "out/prod/main.js"
                                   :output-dir "out/prod"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :output-wrapper false
                                   :preamble ["moment/min/moment.min.js"
                                              "marked/marked.min.js"]
                                   :externs ["moment/moment.js"
                                             "marked/lib/marked.js"]
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]})
