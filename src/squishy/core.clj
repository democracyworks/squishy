(ns squishy.core
  (:require [squishy.data-readers]
            [cemerick.bandalore :as sqs]
            [clojure.tools.logging :as log]))

(defn client [access-key secret-key region]
  (doto (sqs/create-client access-key
                           secret-key)
    (.setRegion region)))

(defn report-error [client fail-queue-url body error]
  (let [fail-message (pr-str {:body body :error (.getMessage error)})]
    (sqs/send client fail-queue-url fail-message)))

(defn safe-process [client fail-queue-url f]
  (fn [message]
    (log/debug "Processing SQS message:" (str "<<" message ">>"))
    (try
      (f message)
      (catch Throwable e
        (let [body (:body message)]
          (log/error "Failed to process" body e)
          (report-error client fail-queue-url body e))))))

(defn consume-messages
  [creds queue-name fail-queue-name f]
  (future
    (loop []
      (let [client (client (:access-key creds)
                           (:access-secret creds)
                           (:region creds))
            queue-url (sqs/create-queue client queue-name)
            fail-queue-url (sqs/create-queue client fail-queue-name)
            consume (sqs/deleting-consumer client (safe-process client fail-queue-url f))]
        (when (try
                (log/info "Consuming SQS messages from" queue-url)
                (doseq [message (sqs/polling-receive client queue-url :max-wait Long/MAX_VALUE :limit 10)]
                  (consume message))
                (catch Throwable t
                  (log/error "Failed to consume-messages" t)
                  :keep-trying))
          (recur))))))
