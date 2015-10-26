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

(def backoff-start 1000)
(def max-failures  10)

(defn consume-messages
  [creds queue-name fail-queue-name f]
  (future
    (let [rec (atom {:failures 0
                     :backoff  backoff-start})]
      (loop []
        (when (try
                (let [client (client (:access-key creds)
                                     (:access-secret creds)
                                     (:region creds))
                      queue-url (sqs/create-queue client queue-name)
                      fail-queue-url (sqs/create-queue client fail-queue-name)
                      consume (sqs/deleting-consumer client (safe-process client fail-queue-url f))]
                  (log/info "Consuming SQS messages from" queue-url)
                  (doseq [message (sqs/polling-receive client queue-url :max-wait Long/MAX_VALUE :limit 10)]
                    (reset! rec {:failures 0 :backoff backoff-start})
                    (consume message)))
                (catch Throwable t
                  (log/error "Failed to consume-messages" t)
                  (swap! rec update :failures inc)
                  :keep-trying))
          (let [{:keys [failures backoff]} @rec]
            (if (>= failures max-failures)
              (log/fatal "Failed" failures "times, exiting.")
              (do
                (Thread/sleep backoff)
                (swap! rec update :backoff (partial * 2))
                (recur)))))))))
