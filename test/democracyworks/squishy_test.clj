(ns democracyworks.squishy-test
  (:require [clojure.test :refer :all]
            [democracyworks.squishy :refer :all :as squishy]
            [cemerick.bandalore :as sqs]))

(deftest consume-messages-test
  (testing "calls function arg on each msg"
    (let [result (atom [])]
      (with-redefs [squishy/get-queue (constantly nil)
                    sqs/delete (constantly nil)
                    sqs/polling-receive (constantly ["msg 1" "msg 2"])]
        (is (= ["MSG 1" "MSG 2"]
               (do
                 @(consume-messages nil
                                    #(swap! result conj
                                            (clojure.string/upper-case %)))
                 @result))))))

  (testing "deletes msgs as it consumes them"
    (let [queue (atom ["msg 1" "msg 2"])]
      (with-redefs [squishy/get-queue (constantly nil)
                    sqs/polling-receive (constantly @queue)
                    sqs/delete (fn [client message]
                                 (swap! queue #(drop 1 %)))]
        (is (= []
               (do
                 @(consume-messages nil (constantly nil))
                 @queue))))))

  (testing "puts msgs on fail queue when processing fn throws exception"
    (let [fail-queue (atom [])]
      (with-redefs [squishy/get-queue (constantly nil)
                    squishy/get-fail-queue (constantly nil)
                    sqs/polling-receive (constantly [{:body "msg 1"}
                                                     {:body "msg 2"}])
                    sqs/delete (constantly nil)
                    sqs/send (fn [client q msg] (swap! fail-queue conj msg))]
        (is (= ["{:body \"msg 1\", :error \"msg 1 failed\"}"
                "{:body \"msg 2\", :error \"msg 2 failed\"}"]
               (do @(consume-messages
                     nil #(throw (Exception. (str (:body %) " failed"))))
                   @fail-queue)))))))
