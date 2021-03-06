(ns squishy.core
  (:require [squishy.data-readers :as dr]
            [cemerick.bandalore :as sqs]
            [clojure.tools.logging :as log])
  (:import  [com.amazonaws.regions Region]))

(def default-visibility-timeout 30) ; seconds

(defmulti client
  "Construct an SQS client instance from the provided credentials and region.
   Region can be a string in either name (`us-east-1`) or enum (`US_EAST_1`)
   format, or it can be an instance of com.amazonaws.regions.Region already."
  (fn [_ _ region] (type region)))

(defmethod client com.amazonaws.regions.Region [access-key secret-key region]
  (doto (sqs/create-client access-key secret-key)
    (.setRegion region)))

(defmethod client java.lang.String [access-key secret-key region]
  (client access-key secret-key (dr/aws-region region)))

(defmethod client :default [_ _ region]
  (throw (RuntimeException. "Region must either be a string in either \"us-west-1\" or \"US_WEST_1\" format or an instance of com.amazonaws.regions.Region. You can also use the `#aws/region` data-reader tag in a configuration to convert a string in the above formats to the Java instance.")))

(defn report-error [client fail-queue-url body error]
  (let [fail-message (pr-str {:body body :error (.getMessage error)})]
    (sqs/send client fail-queue-url fail-message)))

(defn safe-process
  [client queue-url fail-queue-url visibility-timeout delete-callback f]
  (fn [message]
    (log/debug "Processing SQS message:" (pr-str message))
    (let [timeout-increaser (future
                              (loop [timeout visibility-timeout]
                                (let [new-timeout (int (* timeout 1.5))]
                                  (Thread/sleep (* 1000 timeout 0.75))
                                  (sqs/change-message-visibility client queue-url message new-timeout)
                                  (recur new-timeout))))
          callback (when delete-callback
                     (fn []
                       (sqs/delete client message)
                       (future-cancel timeout-increaser)))]
      (try (if callback
             (f message callback)
             (f message))
           (catch Exception e
             (let [body (:body message)]
               (log/error e "Failed to process" body)
               (report-error client fail-queue-url body e)))
           (finally
             (future-cancel timeout-increaser))))))

(def backoff-start 1000)
(def max-failures  2)

(defonce consumer-futures (atom {}))

(defn with-backoff* [backoff-start max-failures consumer-id f]
  (let [rec (atom 0)
        reset (fn [] (reset! rec 0))]
    (loop []
      (when (try
              (f reset)
              (swap! consumer-futures dissoc consumer-id)
              nil
              (catch Exception t
                (when (get @consumer-futures consumer-id)
                  (log/error "Failure:" t)
                  (swap! rec inc)
                  :keep-trying)))
        (if (>= @rec max-failures)
          (log/fatal "Failed" @rec "times, exiting.")
          (do
            (Thread/sleep (* backoff-start (Math/pow 2 (dec @rec))))
            (when (get @consumer-futures consumer-id)
              (recur))))))))

(defmacro with-backoff [[reset backoff-start max-failures consumer-id] & body]
  `(with-backoff* ~backoff-start ~max-failures ~consumer-id
     (fn [~reset] ~@body)))

(defn consume-messages
  ([creds queue-name fail-queue-name f]
   (consume-messages creds queue-name fail-queue-name
                     {:visibility-timeout default-visibility-timeout}
                     f))
  ([creds queue-name fail-queue-name options f]
   (let [consumer-id (java.util.UUID/randomUUID)
         message-future
         (future
           (with-backoff [reset backoff-start max-failures consumer-id]
             (let [client (client (:access-key creds)
                                  (:access-secret creds)
                                  (:region creds))
                   queue-url (sqs/create-queue client queue-name)
                   fail-queue-url (sqs/create-queue client fail-queue-name)
                   visibility-timeout (get options :visibility-timeout
                                           default-visibility-timeout)
                   delete-callback (get options :delete-callback false)
                   consume (sqs/deleting-consumer
                            client
                            (safe-process client
                                          queue-url
                                          fail-queue-url
                                          visibility-timeout
                                          delete-callback
                                          f))]
               (log/info "Consuming SQS messages from" queue-url)
               (doseq [message (sqs/polling-receive client
                                                    queue-url
                                                    :max-wait Long/MAX_VALUE
                                                    :limit 10)]
                 (reset)
                 (consume message)))))]
     (swap! consumer-futures assoc consumer-id message-future)
     consumer-id)))

(defn stop-consumer [consumer-id]
  (swap! consumer-futures dissoc consumer-id))
