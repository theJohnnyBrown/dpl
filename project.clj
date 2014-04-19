(defproject dpl "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]

                 ;; cljs deps
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                 [om "0.5.3"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [secretary "1.1.0"]
                 ;; dev version, for React.DOM.nobr issue
                 [sablono "0.2.15"]
                 [hiccups "0.3.0"]

                 ;; shared - yay!
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                 [formative "0.8.8"]
                 [jkkramer/verily "0.6.0"]

                 ;; cljvm deps
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                 [liberator "0.11.0"]
                 [compojure "1.1.6"]
                 [ring "1.2.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [cheshire "5.3.1"]
                 ;; db stuff
                 [org.clojure/java.jdbc "0.3.3"]
                 [postgresql/postgresql "9.3-1101.jdbc4"]
                 [korma "0.3.0"]]

   :jvm-opts ^:replace ["-Xmx1024m" "-server"]

  :plugins [[lein-npm "0.4.0"]
            [lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.10"]
            [com.keminglabs/cljx "0.3.2"]]

  :cljx {:builds [{:source-paths ["cljx-src"]
                 :output-path "target/classes"
                 :rules :clj}

                {:source-paths ["cljx-src"]
                 :output-path "target/generated/cljs"
                 :rules :cljs}]}

  :ring {
    :handler dpl.api1/handler
    :port 8000
    :nrepl {:start? true :port 4555}}

  :cljsbuild {
    :builds [{:id "node"
              :source-paths ["src" "target/generated/cljs"]
              :compiler {
                :language-in :ecmascript5
                :target :nodejs
                :output-to "target/server.js"
                :optimizations :simple
                :pretty-print true
                :preamble ["dpl/react_preamble.js"]
                :externs ["react/externs/react.js"]}}
             {:id "browser-dev"
              :source-paths ["src" "target/generated/cljs"]
              :compiler {
                :language-in :ecmascript5
                :output-to "resources/public/js/app-dev.js"
                :optimizations :simple
                :pretty-print true
                :preamble ["react/react.js"]
                :externs ["react/externs/react.js"]}}
             {:id "browser"
              :source-paths ["src" "target/generated/cljs"]
              :compiler {
                :output-to "resources/public/js/app.js"
                :optimizations :advanced
                :pretty-print true
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]}}]}

  ;; node deps (used by cljs)
  :node-dependencies [[react "0.9.0"]
                      [express "3.4.8"]
                      [logfmt "0.20.0"]
                      [restler "3.2.0"]
                      [http-proxy "1.1.1"]])
