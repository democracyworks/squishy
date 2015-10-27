(ns squishy.core
  (:require [squishy.data-readers]
            [cemerick.bandalore :as sqs]
            [clojure.tools.logging :as log]))

(def ^:dynamic *visibility-timeout* 30) ; seconds; the SQS default

(defn client [access-key secret-key region]
  (doto (sqs/create-client access-key
                           secret-key)
    (.setRegion region)))

(defn report-error [client fail-queue-url body error]
  (let [fail-message (pr-str {:body body :error (.getMessage error)})]
    (sqs/send client fail-queue-url fail-message)))

(defn safe-process [client queue-url fail-queue-url f]
  (fn [message]
    (log/debug "Processing SQS message:" (str "<<" message ">>"))
    (let [processor (future
                      (try (f message)
                           (catch Exception e
                             (let [body (:body message)]
                               (log/error e "Failed to process" body)
                               (report-error client fail-queue-url body e)))))]
      (loop [timeout *visibility-timeout*]
        (if (realized? processor)
          @processor
          (let [new-timeout (int (* timeout 1.5))]
            (Thread/sleep (* 1000 (- timeout (/ timeout 4))))
            (sqs/change-message-visibility client queue-url message new-timeout)
            (recur new-timeout)))))))

(defn consume-messages
  [client queue-name fail-queue-name f]
  (let [queue-url (sqs/create-queue client queue-name)
        fail-queue-url (sqs/create-queue client fail-queue-name)]
    (future
      (do
        (log/info "Consuming SQS messages from" queue-url)
        (dorun
         (map (sqs/deleting-consumer client (safe-process client queue-url fail-queue-url f))
              (sqs/polling-receive client queue-url :max-wait Long/MAX_VALUE :limit 10)))))))
