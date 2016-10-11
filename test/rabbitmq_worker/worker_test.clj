(ns rabbitmq-worker.worker-test
  (:require [clojure.test :refer :all]
            [rabbitmq-worker.worker :as worker]
            [langohr.basic]
            [langohr.core :as langohr.core]
            [langohr.channel :as langohr.channel]
            [langohr.confirm]
            [langohr.queue]
            [clojure.tools.logging :as log]))

(def rabbitmq-config {:host "127.0.0.1", :port 5672, :vhost "/", :password "guest", :username "guest"})

(def default-exhange "")

(defn publish-msg [queue payload]
  (let [connection (langohr.core/connect rabbitmq-config)
        channel (langohr.channel/open connection)]
    (try
      (langohr.queue/declare channel queue {:durable     true
                                            :auto-delete false
                                            :arguments   {}})
      (langohr.confirm/select channel)
      (langohr.basic/publish channel default-exhange queue payload {:content-type "text/plain" :mandatory true})
      (langohr.confirm/wait-for-confirms channel)
      (println "published"))))

(deftest consume-messages
  (let [my-promise (promise)
        consumer-fn (fn consumer-fn [payload]
                      (log/info "delivery consumed by inner handler, payload:" payload)
                      (deliver my-promise payload))
        [connection channel] (worker/consume rabbitmq-config "test-queue-1" consumer-fn)]
    (publish-msg "test-queue-1" "banan")
    (is (= "banan" (deref my-promise 1000 :timeout)))
    (langohr.core/close connection)))

(deftest handle-errors
  (let [my-promise (promise)
        consumer-fn (fn consumer-fn [payload]
                      (log/info "delivery consumed by inner handler, payload:" payload)
                      (when (= payload "explode!")
                        (throw (Exception. "boom!")))
                      (deliver my-promise payload))
        [connection channel] (worker/consume rabbitmq-config "test-queue-2" consumer-fn)]
    (publish-msg "test-queue-2" "explode!")
    (publish-msg "test-queue-2" "potato")
    (is (= "potato" (deref my-promise 100 :timeout)))))
(langohr.core/close connection)
