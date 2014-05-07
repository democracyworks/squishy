(ns democracyworks.squishy.core
  (:require [squishy.data-readers]
            [cemerick.bandalore :as sqs]
            [turbovote.resource-config :refer [config]]
            [clojure.tools.logging :refer [error info]]))

(defn client []
  (doto (sqs/create-client (config :aws :creds :access-key)
                           (config :aws :creds :secret-key))
    (.setRegion (config :aws :sqs :region))))

(defn- create-queue [client queue-key]
  (sqs/create-queue client (config :aws :sqs queue-key)))

(def ^:private memoized-create-queue (memoize create-queue))

(defn- get-queue [client]
  (memoized-create-queue client :queue))

(defn- get-fail-queue [client]
  (memoized-create-queue client :fail-queue))

(defn report-error [client body error]
  (let [q (get-fail-queue client)]
    (sqs/send client q (pr-str {:body body :error error}))))

(defn safe-process [client f]
  (fn [message]
    (info "Processing SQS message:" (str "<<" message ">>"))
    (try (f message)
      (catch Exception e
        (let [body (:body message)]
          (error "Failed to process" body e)
          (report-error client body e))))))

(defn consume-messages
  [client f]
  (let [q (get-queue client)]
    (future
      (do
        (info "Consuming SQS messages from" q)
        (dorun
         (map (sqs/deleting-consumer client (safe-process client f))
              (sqs/polling-receive client q :max-wait Long/MAX_VALUE :limit 10)))))))
