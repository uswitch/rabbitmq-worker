(defproject rabbitmq-worker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.novemberain/langohr "3.6.1"]

                 ;logging
                 [org.clojure/tools.logging  "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [net.logstash.logback/logstash-logback-encoder "4.5.1"]
                 [org.slf4j/slf4j-api "1.7.12"]
                 [org.slf4j/log4j-over-slf4j "1.7.12"]])
