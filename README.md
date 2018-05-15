# rabbitmq-worker

A Clojure library that wraps the excellent [Langohr](http://clojurerabbitmq.info/) library in order to hide the connecting to RabbitMQ,
, consuming, acking/nacking of messages, publishing etc. For us this has been suprisingly error / confusion prone in projects that roll their own setup.

This library tries to be a "Sugar-coated API for task queues that hides all the AMQP machinery from the developer", [unlike the quite low level langohr](http://clojurerabbitmq.info/articles/getting_started.html#what-langohr-is-not). This library provides sensible defaults for most things, in an attempt to give a maximally simple interface to consume from queues and publish messages, so that you as a developer don't have to think as much.

# Usage

## Getting the library
For leiningen: 
```[uswitch/rabbitmq-worker "0.3.1"]```

### Opening a connection

```clojure
(require '[rabbitmq-worker.worker :as worker])

(worker/open-connection {:uri "amqp://guest:guest@localhost:5672"})

(worker/open-connection {:host "127.0.0.1", :port 5672, :vhost "/", :password "guest", :username "guest"})
```

## Consuming messages

From default exchange:

```clojure
(worker/consume connection 
                {:queue "banana"} 
                (fn [message] (println message)))
```

The queue will be automatically declared with the defaults `:queue-auto-delete false`, `:queue-durable true`  and `:queue-exclusive false`.

From another exchange:

```clojure
(worker/consume connection 
                {:exchange "banana-exchange" :queue "fruits"} 
                (fn [message] (println message)))
```

Messages that throw an error will be automatically pushed onto a queue named `{original-queue-name}-failed`. The failed queue is declared with `:failed-queue-auto-delete false`, `:failed-queue-durable  true` and `:failed-queue-exclusive false`.

The following map shows all the options supported if you need to configure more advanced consumers, but don't override unless you know what you're doing.

```clojure
{:queue                    String
 :queue-auto-delete        Boolean
 :queue-exclusive          Boolean
 :queue-durable            Boolean
 :queue-arguments          {:x-dead-letter-exchange String
                            :x-dead-letter-routing-key String}
 :metadata                 Boolean (provide metadata to message hander function as 2nd arg.)
 :raw-payload              Boolean (provide message as raw payload (Byte array) instead of string)
 :failed-queue             String
 :failed-queue-auto-delete Boolean
 :failed-queue-durable     Boolean
 :failed-queue-exclusive   Boolean}
```

## Handling errors

Messages are automatically acknowledged if the handler successfully processes the payload and automatically rejected if it fails. The consumer config accepts an `:on-error` key, which is a function that will be invoked when an error happens. The function should take the error and the received payload.

```clojure
(worker/consume connection
                {:queue    "pineapple-queue"
                 :on-error (fn [error payload] (log/error error))}
                (fn [message] (println message)))
```

# Development

### Running tests
For local testing with lein test, you need a RabbitMQ instance running locally.

# License

Copyright © 2017-2018 uSwitch.

Distributed under the Eclipse Public License, the same as Clojure.
