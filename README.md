# rabbitmq-worker
[![Build Status](https://drone.uswitchinternal.com/api/badges/uswitch/rabbitmq-worker/status.svg)](https://drone.uswitchinternal.com/uswitch/rabbitmq-worker)

A Clojure library that wraps langohr that hides the connecting to RabbitMQ,
, consuming, acking/nacking of messages, publishing etc. This has been suprisingly error / confusion prone in projects that roll their own setup.

### Usage

#### Opening a connection


```clojure
(require '[rabbitmq-worker.worker :as worker])

(worker/open-connection {:uri "amqp://guest:guest@localhost:5672"})

(worker/open-connection {:host "127.0.0.1", :port 5672, :vhost "/", :password "guest", :username "guest"})
```

#### Consuming messages

From default exchange:

```clojure
(worker/consume connection {:queue "energy-pal-batched-switches"} (fn [message] (println message)))
````

The queue will be automatically declared with the defaults `:durable true`, `:auto-delete false` and `:exclusive false`.

From another exchange:
```clojure
(worker/consume connection {:exchange "energy.switch.batched" :queue "energy-pal-batched-switches"} (fn [message] (println message)))
```

Messages that throw an error will be automatically pushed onto a queue named "{original-queue}-failed". The failed queue is declared with `:auto-delete false``, `:durable true` and `:exclusive false`.

The following map shows all the options supported if you need to configure more advanced consumers, but don't override unless you know what you're doing.
```clojure
{:queue             String
 :queue-auto-delete Boolean
 :queue-exclusive   Boolean
 :queue-durable     Boolean
 :queue-arguments   {:x-dead-letter-exchange String
                     :x-dead-letter-routing-key String}
 :failed-queue             String
 :failed-queue-auto-delete Boolean
 :failed-queue-durable     Boolean
 :failed-queue-exclusive   Boolean}
```

#### Handling errors

Messages are automatically acknowledged if the handler successfully processes the payload and automatically rejected if it fails. The consumer config accepts an `:on-error` key, which is a function that will be invoked when an error happens. The function should take the error and the received payload.
```clojure
(worker/consume connection
                {:queue    "energy-pal-batched-switches"
                 :on-error (fn [error payload] (log/error error))}
                (fn [message] (println message)))
```

### Running tests
The tests should run automatically in drone. For local testing with lein test, you need a RabbitMQ instance running locally.


# License

Copyright © 2015 uSwitch.

Distributed under the Eclipse Public License, the same as Clojure.
