# squishy

A Clojure library for safely and reliably consuming SQS messages.

## Usage

```clj
(ns demo
  [squishy.core :as sqs])

(defonce (atom consumer-id nil))

(defn process-message [message]
  (println "Processing: " message)
  ;; ... do something fancy
  )

(let [creds {:access-key "AKIA..."
             :access-secret "8jcNZ9mM..."
             :region #aws/region "US_EAST_1"}
      cid (sqs/consume-messages creds
                                "incoming-queue"
                                "failure-queue"
                                process-message)]
  (reset! consumer-id cid))

;; later when you are done:

(sqs/stop-consumer @consumer-id)
```

### Shutting down

**IMPORTANT:** Squishy is quite aggressive at recovering from exceptions and
resuming processing of messages. This behavior is necessary to maintain 
long-term reliable connections to SQS.

A side effect of this is that squishy will interpret the exceptions that it 
receives when you stop / undeploy your app as things to recover from. If you
see the "Consuming SQS messages from..." log message after attempting to stop a
squishy app, then you are running into this problem.

As of version 3.0.0, you **MUST** keep the `consmer-id` returned by 
`consume-messages` and call `(squishy.core/stop-consumer consumer-id)` when
you are shutting down / undeploying your app. This will tell squishy to stop
trying to recover that consumer.

### Visibility timeouts

In version 3.0.0, squishy gained the ability to adjust the visibility timeout
of the messages it's processing automatically. The visibility timeout default
is 30 seconds, which means that 30 seconds after a consumer starts processing a
message, SQS will make it available to other consumers. Since this is not 
usually what you expect or want, squishy now informs SQS that it should 
increase the visibility timeout of individual messages when it knows that the
provided processing function is still processing that message. This means that
other consumers won't see that message.

If your SQS queue has a different visibility timeout configured, you can use
the 5-arity version of `consume-messages` to tell squishy when it should raise
the visibility timeout. So, for example, if you have configured your queue with
a 300-second visibility timeout, you should call `consume-messages` like this:

```
(sqs/consume-messages creds
                      "incoming-queue"
                      "failure-queue"
                      {:visibility-timeout 300}
                      process-message)
```

It doesn't hurt to not do this as squishy assumes 30 second visibility timeouts
by default. It would just result in more SQS API calls than are strictly
necessary.

## License

Copyright © 2014-2016 Democracy Works, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
