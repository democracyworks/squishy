(ns squishy.core
  (:require [squishy.data-readers]
            [cemerick.bandalore :as sqs]
            [clojure.tools.logging :as log]))

(def default-visibility-timeout 30) ; seconds

(defn client [access-key secret-key region]
  (doto (sqs/create-client access-key
                           secret-key)
    (.setRegion region)))

(defn report-error [client fail-queue-url body error]
  (let [fail-message (pr-str {:body body :error (.getMessage error)})]
    (sqs/send client fail-queue-url fail-message)))

(defn safe-process
  [client queue-url fail-queue-url visibility-timeout f]
  (fn [message]
    (log/debug "Processing SQS message:" (pr-str message))
    (let [timeout-increaser (future
                              (loop [timeout visibility-timeout]
                                (let [new-timeout (int (* timeout 1.5))]
                                  (Thread/sleep (* 1000 timeout 0.75))
                                  (sqs/change-message-visibility client queue-url message new-timeout)
                                  (recur new-timeout))))]
      (try (f message)
           (catch Exception e
             (let [body (:body message)]
               (log/error e "Failed to process" body)
               (report-error client fail-queue-url body e)))
           (finally
             (future-cancel timeout-increaser))))))

(def backoff-start 1000)
(def max-failures  10)

(defn with-backoff* [backoff-start max-failures f]
  (let [rec (atom 0)
        reset (fn [] (reset! rec 0))]
    (loop []
      (when (try
              (f reset)
              nil
              (catch Exception t
                (log/error "Failure:" t)
                (swap! rec inc)
                :keep-trying))
        (if (>= @rec max-failures)
          (log/fatal "Failed" @rec "times, exiting.")
          (do
            (Thread/sleep (* backoff-start (Math/pow 2 (dec @rec))))
            (recur)))))))

(defmacro with-backoff [[reset backoff-start max-failures] & body]
  `(with-backoff* ~backoff-start ~max-failures
     (fn [~reset] ~@body)))

(defn consume-messages
  ([creds queue-name fail-queue-name f]
   (consume-messages creds queue-name fail-queue-name
                     {:visibility-timeout default-visibility-timeout}
                     f))
  ([creds queue-name fail-queue-name options f]
   (future
     (with-backoff [reset backoff-start max-failures]
       (let [client (client (:access-key creds)
                            (:access-secret creds)
                            (:region creds))
             queue-url (sqs/create-queue client queue-name)
             fail-queue-url (sqs/create-queue client fail-queue-name)
             visibility-timeout (get options :visibility-timeout
                                     default-visibility-timeout)
             consume (sqs/deleting-consumer client
                                            (safe-process client
                                                          queue-url
                                                          fail-queue-url
                                                          visibility-timeout
                                                          f))]
         (log/info "Consuming SQS messages from" queue-url)
         (doseq [message (sqs/polling-receive client
                                              queue-url
                                              :max-wait Long/MAX_VALUE
                                              :limit 10)]
           (reset)
           (consume message)))))))

