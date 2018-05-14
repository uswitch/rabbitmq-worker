(ns rabbitmq-worker.worker
  (:require [clojure.tools.logging :as log]
            [langohr.core :as langohr-core]
            [langohr.basic :as langohr-basic]
            [langohr.channel :as langohr-channel]
            [langohr.queue :as langohr-queue]
            [langohr.consumers :as langohr-consumers]
            [clojure.string :as s]
            [langohr.confirm :as langohr-confirm]))

(def default-exchange-name "")

(defn- make-shutdown-handler [connection]
  (fn [tag reason]
    (when-not (langohr-core/closed? connection)
      (log/errorf "Something went wrong with the rabbitmq consumer! Stopping consumer %s because of %s" tag reason)
      (langohr-core/close connection))))

(defn safely [f]
  (fn [& [error & rest :as args]]
    (try
      (apply f args)
      (catch Throwable t
        (log/error t "Error occurred in error handler")
        (throw (ex-info "Error when calling error handler" {:args           rest
                                                            :original-error error}
                        t))))))

(defn- wrap-message-acknowledgement
  "handler is function provided by library user: args [message] or if metadata-requested is enabled [message metadata]"
  [handler error-handler metadata-requested]
  (fn [ch {:keys [delivery-tag] :as meta} ^bytes payload]
    (try
      (log/tracef "Received message %d" delivery-tag)
      (let [message (String. payload "UTF-8")]
        (if metadata-requested
          (handler message meta)
          (handler message))
        (log/tracef "ACKing message with delivery tag %d" delivery-tag)
        (langohr-basic/ack ch delivery-tag))
      (catch Throwable t
        (log/error t "Exception occured while processing message." delivery-tag " Message will be rejected.")
        (langohr-basic/reject ch delivery-tag)
        (let [handle-error (safely error-handler)]
          (handle-error t payload))))))

(defn open-connection
  "Thin wrapper around langohrs connect function. Remember to call the close-connection function once you are done!

  parameters:
  rabbitmq-config: Will be passed to langohr unchanged. Should look something like
  {:host \"127.0.0.1\", :port 5672, :vhost \"/\", :password \"guest\", :username \"guest\"}"
  [rabbitmq-config]
  (langohr-core/connect rabbitmq-config))

(defn close-connection [connection]
  (langohr-core/close connection))

(defn- failed-queue-name [queue-name]
  (str queue-name "-failed"))

(defn consume
  "Starts consumption of messages off RabbitMQ in another thread, using a message handler function.
   Will ack the message when the message handler completes without an exception, and reject the message
   when an exception is thrown.

   It sets up the queue to consume from and the bindings to the specified exchange automatically.
   It also configures a failure queue where rejected messages go (by appending \"-failed\"
   onto the end of the specified queue name).

   Parameters:
      connection: Will be passed to langohr unchanged. Set it up using the open-connection function.

      consumer-config: For simple consumers, it will look like this: {:exchange String :queue-name String}. It will
      use the default exchange if none is provided. The consumer config can also include an :on-error key with a
      a function as a value. The function will be invoked with the error thrown and the received payload
      after the message has been rejected.

      message-handler-fn: Your function to handle incomming messages. Gets the message contents as a string.
                          Example:  (fn message-handler [message]
                                       (let [switch (json/read-json message)]
                                         (log/info \"Inserting switch\")
                                         (insert-switch-into-database switch)))

   Advanced usage:

   The following map shows all the options supported if you need to configure more advanced consumers.
   Don't override unless you know what you're doing.
   {:queue            String
    :queue-auto-delete Boolean
    :queue-exclusive   Boolean
    :queue-durable     Boolean
    :queue-arguments   {:x-dead-letter-exchange String
                       :x-dead-letter-routing-key String}
    :metadata                 Boolean (provide metadata to message hander function as 2nd arg.)
    :failed-queue             String
    :failed-queue-auto-delete Boolean
    :failed-queue-durable     Boolean
    :failed-queue-exclusive   Boolean}"
  [connection consumer-config message-handler-fn]
  {:pre [(some? (:exchange consumer-config "")) (some? (:queue consumer-config))]}
  (log/info "Starting RabbitMQ consumption")
  (let [exchange-name       (get consumer-config :exchange default-exchange-name)
        queue-name          (:queue consumer-config)
        failed-queue-name   (get consumer-config :failed-queue (failed-queue-name queue-name))
        metadata            (get consumer-config :metadata false)
        queue-config        {:exclusive   (get consumer-config :queue-exclusive false)
                             :auto-delete (get consumer-config :queue-auto-delete false)
                             :durable     (get consumer-config :queue-durable true)
                             :arguments   (get consumer-config :queue-arguments {"x-dead-letter-exchange"    ""
                                                                                 "x-dead-letter-routing-key" failed-queue-name})}
        failed-queue-config {:exclusive   (get consumer-config :failed-queue-exclusive false)
                             :auto-delete (get consumer-config :failed-queue-auto-delete false)
                             :durable     (get consumer-config :failed-queue-durable true)}
        channel             (langohr-channel/open connection)
        queue-declaration   (langohr-queue/declare channel queue-name queue-config)
        queue-name          (:queue queue-declaration)
        error-handler       (get consumer-config :on-error (constantly :default))]
    (log/debug "Declared the queue: " queue-name)
    (langohr-basic/qos channel 1)

    ;;Avoid declaring a failed-failed queue when consuming off a failure queue
    (when-not (.contains queue-name "-failed")
      (log/debug "Declaring failed queue: " failed-queue-name)
      (langohr-queue/declare channel failed-queue-name failed-queue-config))

    (if (= exchange-name default-exchange-name)
      ;; The default exchange is already bound to all queues, routing messages by routing key = queue name
      ;; So no need to bind anything
      (log/info "Using the default exchange")
      (do
        (log/infof "Binding queue %s to exhange %s" queue-name exchange-name)
        (langohr-queue/bind channel queue-name exchange-name)))

    (log/info "Starting consumption off queue:" queue-name)
    (langohr-consumers/subscribe channel
                                 queue-name
                                 (wrap-message-acknowledgement message-handler-fn error-handler metadata)
                                 {:handle-shutdown-signal-fn (make-shutdown-handler connection)})

    (log/info "RabbitMQ setup done, the Langohr RabbitMQ consumer should now be running in another thread.")
    channel))

(defn publish
  "Published a string to a queue, using the default exchange.
   Declares the queue first, creating it if it's not there.

   Parameters:
     connection:  The rabbitmq connection.
                  please set it up yourself using the open-connection function. Remember to close it when you are done!

     queue-name:  Self-explanatory I hope.

     payload:     A string to publish on the queue.

     queue-options (optional): langohr options to pass when setting up the queue. It is merged with the defaults that are:
                                      {:exclusive   false
                                       :auto-delete false
                                       :durable     true
                                       :arguments   {\"x-dead-letter-exchange\"    \"\"
                                                     \"x-dead-letter-routing-key\" (str queue-name \"-failed\")}}
     "
  ([connection queue-name payload]
   (publish connection queue-name payload {}))
  ([connection queue-name payload queue-options]
   (let [channel       (langohr.channel/open connection)
         queue-options (merge {:exclusive   false
                               :auto-delete false
                               :durable     true
                               :arguments   {"x-dead-letter-exchange"    ""
                                             "x-dead-letter-routing-key" (failed-queue-name queue-name)}}
                              queue-options)]
     (langohr-queue/declare channel queue-name queue-options)
     (langohr-confirm/select channel)
     (langohr-basic/publish channel default-exchange-name queue-name payload {:content-type "text/plain" :mandatory true})
     (langohr-confirm/wait-for-confirms channel)
     (langohr.channel/close channel))
   (log/info "published message to queue" queue-name)))

(defn queue-length [connection queue-name]
  (langohr-queue/message-count (langohr.channel/open connection) queue-name))
