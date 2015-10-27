(ns squishy.core-test
  (:require [clojure.test :refer :all]
            [squishy.core :refer :all]
            [cemerick.bandalore :as sqs]))

(deftest consume-messages-test
  (testing "calls function arg on each msg"
    (let [result (atom [])]
      (with-redefs [sqs/create-queue (constantly nil)
                    sqs/delete (constantly nil)
                    sqs/change-message-visibility (constantly nil)
                    sqs/polling-receive (constantly ["msg 1" "msg 2"])]
        (binding [*visibility-timeout* 0.1]
          (is (= ["MSG 1" "MSG 2"]
                 (do
                   @(consume-messages nil nil nil
                                      #(swap! result conj
                                              (clojure.string/upper-case %)))
                   @result)))))))

  (testing "deletes msgs as it consumes them"
    (let [queue (atom ["msg 1" "msg 2"])]
      (with-redefs [sqs/create-queue (constantly nil)
                    sqs/change-message-visibility (constantly nil)
                    sqs/polling-receive (constantly @queue)
                    sqs/delete (fn [client message]
                                 (swap! queue #(drop 1 %)))]
        (binding [*visibility-timeout* 0.1]
          (is (= []
                 (do
                   @(consume-messages nil nil nil (constantly nil))
                   @queue)))))))

  (testing "puts msgs on fail queue when processing fn throws exception"
    (let [fail-queue (atom [])]
      (with-redefs [sqs/create-queue (constantly nil)
                    sqs/change-message-visibility (constantly nil)
                    sqs/polling-receive (constantly [{:body "msg 1"}
                                                     {:body "msg 2"}])
                    sqs/delete (constantly nil)
                    sqs/send (fn [client q msg] (swap! fail-queue conj msg))]
        (binding [*visibility-timeout* 0.1]
          (is (= ["{:body \"msg 1\", :error \"msg 1 failed\"}"
                  "{:body \"msg 2\", :error \"msg 2 failed\"}"]
                 (do @(consume-messages
                       nil nil nil #(throw (Exception. (str (:body %) " failed"))))
                     @fail-queue))))))))
