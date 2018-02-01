(defproject dvlopt/mcp342x
            "0.0.0-alpha2"

  :description "A/D with MCP342x converters via I²C"
  :url         "https://github.com/dvlopt/mcp342x.clj"
  :license     {:name "Eclipse Public License"
                :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles    {:dev {:source-paths ["dev"]
                      :main         user
                      :dependencies [[org.clojure/clojure    "1.9.0"]
                                     [org.clojure/test.check "0.10.0-alpha2"]
                                     [criterium              "0.4.4"]
                                     [dvlopt/icare           "0.0.0-alpha1"]]
                      :plugins      [[venantius/ultra "0.5.1"]
                                     [lein-codox      "0.10.3"]]
                      :codox        {:output-path  "doc/auto"
                                     :source-paths ["src"]}
                      :repl-options {:timeout 180000}
                      :global-vars  {*warn-on-reflection* true}}})
