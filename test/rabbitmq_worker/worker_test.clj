(ns rabbitmq-worker.worker-test
  (:require [clojure.test :refer :all]
            [rabbitmq-worker.worker :as worker]
            [clojure.tools.logging :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These tests require a RabbitMQ instance running locally ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *connection*)

(defn make-connection []
  (worker/open-connection {:host "127.0.0.1", :port 5672, :vhost "/", :password "guest", :username "guest"}))

(defn with-connection [f]
  (binding [*connection* (make-connection)]
    (f)
    (worker/close-connection *connection*)))

(use-fixtures :each with-connection)

(defn consumer-fn
  ([promise payload]
   (log/info "Delivery consumed by handler, payload:" payload)
   (deliver promise payload))
  ([promise payload metadata]
   (log/info "Delivery consumed by handler, metadata:" metadata " payload:" payload)
   (deliver promise payload)))

(defn fail-consumer-fn [promise payload]
  (log/info "Delivery consumed by fail handler, payload:" payload)
  (when (= payload "explode!")
    (throw (ex-info "boom!" {:payload payload})))
  (deliver promise payload))

(defn publish-msg
  ([connection queue payload]
   (worker/publish connection queue payload {:auto-delete true
                                             :durable     false
                                             :exclusive   false}))
  ([connection queue payload options]
   (worker/publish connection queue payload options)))

(def consumer-config
  {:queue                    "test-queue"
   :queue-auto-delete        true
   :queue-exclusive          false
   :queue-durable            false
   :failed-queue             "test-queue-failed"
   :failed-queue-auto-delete true
   :failed-queue-exclusive   false
   :failed-queue-durable     false})

(def consumer-config-with-meta
  {:queue                    "test-queue"
   :queue-auto-delete        true
   :queue-exclusive          false
   :queue-durable            false
   :metadata                 true
   :failed-queue             "test-queue-failed"
   :failed-queue-auto-delete true
   :failed-queue-exclusive   false
   :failed-queue-durable     false})

(deftest consume-messages
  (let [my-promise (promise)]

    (worker/consume *connection* consumer-config (partial consumer-fn my-promise))

    (publish-msg *connection* "test-queue" "banana")

    (is (= "banana" (deref my-promise 10 :timeout)))))

(deftest consume-messages-meta-data
  (let [my-promise (promise)]

    (worker/consume *connection* consumer-config-with-meta (fn [payload meta] (consumer-fn my-promise payload meta)))

    (publish-msg *connection* "test-queue" "kiwi")

    (is (= "kiwi" (deref my-promise 10 :timeout)))))

(deftest handle-errors
  (testing "continues consuming messages even after one of the payloads throws an error"
    (let [my-promise  (promise)]

      (worker/consume *connection* consumer-config (partial fail-consumer-fn my-promise))

      (publish-msg *connection* "test-queue" "explode!")
      (publish-msg *connection* "test-queue" "potato")

      (is (= "potato" (deref my-promise 10 :timeout))))))

(deftest errors-go-to-failed-queue
  (let [promise-1 (promise)
        promise-2 (promise)]

    (worker/consume *connection* consumer-config (partial fail-consumer-fn promise-1))

    (publish-msg *connection* "test-queue" "pineapple")
    (is (= "pineapple" (deref promise-1 10 :timeout)))

    (publish-msg *connection* "test-queue" "explode!")
    (is (= :timeout (deref promise-2 10 :timeout)))

    (worker/consume *connection*
                    {:queue             "test-queue-failed"
                     :queue-durable     false
                     :queue-auto-delete true
                     :queue-arguments   {}
                     :queue-exclusive   false}
                    (partial consumer-fn promise-2))

    (is (= "explode!" (deref promise-2 10 :timeout)))))


(deftest accepts-on-error-handler
  (testing "error handler is invoked if given"
    (let [p (promise)]

      (worker/consume *connection*
                      (assoc consumer-config :on-error (fn [_error _payload]
                                                         (deliver p "banana")))
                      (partial fail-consumer-fn p))

      (publish-msg *connection* "test-queue" "explode!")

      (is (= "banana" (deref p 10 :timeout)))))

  (testing "error handler is not invoked if not specified"
    (let [p (promise)]

      (worker/consume *connection* consumer-config (partial fail-consumer-fn p))

      (publish-msg *connection* "test-queue" "explode!")
      (publish-msg *connection* "test-queue" "banana")

      (is (= "banana" (deref p 10 :timeout))))))

(deftest queue-length
  (let [connection (make-connection)]
    (worker/publish connection "test-queue-4" "payload 1" {:exclusive true :durable false})
    (is (= 1 (worker/queue-length connection "test-queue-4")))
    (worker/publish connection "test-queue-4" "payload 2" {:exclusive true :durable false})
    (is (= 2 (worker/queue-length connection "test-queue-4")))
    (worker/close-connection connection)))
