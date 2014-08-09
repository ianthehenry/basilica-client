(defproject basilica-client "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2280"]
                 [om "0.7.0"]
                 [org.clojure/core.async "0.1.319.0-6b1aca-alpha"]
                 [secretary "1.2.0"]
                 ]
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {:output-to "out/dev/main.js"
                                   :output-dir "out/dev"
                                   :optimizations :none
                                   :source-map "out/dev/main.js.map"}}
                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:output-to "out/prod/main.js"
                                   :output-dir "out/prod"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :output-wrapper false
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/react.js"]
                                   :closure-warnings {:externs-validation :off
                                                      :non-standard-jsdoc :off}}}]})
