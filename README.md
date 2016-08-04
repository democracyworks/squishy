# squishy

A Clojure library for safely and reliably consuming SQS messages.

## Usage

```clj
(ns demo
  [squishy.core :as sqs])

(defn process-message [message]
  (println "Processing: " message)
  ;; ... do something fancy
  )

(let [creds {:access-key "AKIA..."
             :access-secret "8jcNZ9mM..."
             :region #aws/region "US_EAST_1"}]
  (sqs/consume-messages creds
                        "incomming-queue"
                        "failure-queue"
                        process-message))
```

## License

Copyright © 2014 Democracy Works, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
