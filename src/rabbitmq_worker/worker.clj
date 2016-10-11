(ns rabbitmq-worker.worker
  (require [clojure.tools.logging :as log]
           [langohr.basic]
           [langohr.core]
           [langohr.channel]
           [langohr.confirm]
           [langohr.queue]
           [langohr.consumers]))
;
;[langohr.core :as rmq]
;[langohr.channel :as lch]
;[langohr.queue :as lq]
;[langohr.consumers :as lc]
;[langohr.basic :as lb]
;[clojure.tools.logging :as log]
;[clojure.data.json :as json]

(defn- make-shutdown-handler [connection]
  (fn [tag reason]
    (when-not (langohr.core/closed? connection)
      (log/errorf "Something went wrong with the rabbitmq consumer! Stopping consumer %s because of %s" tag reason)
      (langohr.core/close connection))))

(defn- message-handler-wrapper [handler]
  (fn [ch {:keys [delivery-tag] :as meta} ^bytes payload]
    (try
      (log/infof "Recieved message %d" delivery-tag)
      (let [message (String. payload "UTF-8")]
        (handler message)
        (log/infof "ACKing messag with delivery tag" delivery-tag)
        (langohr.basic/ack ch delivery-tag))
      (catch Throwable t
        (log/error t "Exception occured while processing switch. Message will be rejected.")
        (langohr.basic/reject ch delivery-tag)))))

(defn consume [rabbitmq-config queue-name message-handler-fn queue-options]
  (log/info "starting rabbitMq switch consuption")
  (let [connection (langohr.core/connect rabbitmq-config)
        channel (langohr.channel/open connection)
        exhange-name "switch-event"
        queue-name (:queue (langohr.queue/declare channel queue-name
                                                  (merge {:exclusive   false
                                                          :auto-delete false
                                                          :durable     true}
                                                         queue-options)))]
    ;:arguments   {"x-dead-letter-exchange"    ""
    ;              "x-dead-letter-routing-key" "back-office-switch-report-failed"}}))]
    (log/info "binding to queue" queue-name)
    (langohr.queue/bind channel queue-name exhange-name)
    (log/info "starting consumer")
    (langohr.consumers/subscribe channel queue-name (message-handler-wrapper message-handler-fn)
                                 {:handle-shutdown-signal-fn (make-shutdown-handler connection)})

    (log/info "thread done, the Langohr rabbitMq consumer should still be running.")
    [connection channel]))


;;todo viktor: binding to channels
