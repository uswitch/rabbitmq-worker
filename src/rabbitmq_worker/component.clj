(ns rabbitmq-worker.component
  (:require [rabbitmq-worker.worker :as worker]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

(defrecord RabbitConnection [config]
  component/Lifecycle
  (start [component]
    (log/info "Connecting to RabbitMQ")
    (assoc component :connection (worker/open-connection config)))
  (stop [component]
    (when-let [connection (:connection component)]
      (log/info "Closing RabbitMQ connection")
      (worker/close-connection connection)
      (assoc component :connection nil))))
