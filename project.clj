(defproject uswitch/rabbitmq-worker "0.3.1"
  :description "wraps langohr"
  :url "https://github.com/uswitch/rabbitmq-worker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.novemberain/langohr "3.6.1"]
                 [org.clojure/tools.logging  "0.3.1"]
                 [com.stuartsierra/component "0.3.2"]]
  :local-repo ".deps/leiningen")
