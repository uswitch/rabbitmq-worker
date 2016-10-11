(ns rabbitmq-worker.component-test
  (:require [rabbitmq-worker.component :as rabbit]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

(deftest can-start-and-stop-rabbitmq-connection
  (let [connection (rabbit/->RabbitConnection {:uri "amqp://guest:guest@localhost:5672"})]
    (is (:connection (component/start connection)))
    (is (not (:connection (component/stop connection))))))
