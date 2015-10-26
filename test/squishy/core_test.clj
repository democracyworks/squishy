(ns squishy.core-test
  (:require [clojure.test :refer :all]
            [squishy.core :refer :all]
            [cemerick.bandalore :as sqs]))

(deftest consume-messages-test
  (testing "calls function arg on each msg"
    (let [result (atom [])]
      (with-redefs [client (constantly nil)
                    sqs/create-queue (constantly nil)
                    sqs/delete (constantly nil)
                    sqs/polling-receive (constantly ["msg 1" "msg 2"])]
        @(consume-messages nil nil nil
                           #(swap! result conj
                                   (clojure.string/upper-case %)))
        (is (= ["MSG 1" "MSG 2"]
               @result)))))

  (testing "deletes msgs as it consumes them"
    (let [queue (atom ["msg 1" "msg 2"])]
      (with-redefs [client (constantly nil)
                    sqs/create-queue (constantly nil)
                    sqs/polling-receive (constantly @queue)
                    sqs/delete (fn [client message]
                                 (swap! queue #(drop 1 %)))]
        @(consume-messages nil nil nil (constantly nil))
        (is (= []
               @queue)))))

  (testing "puts msgs on fail queue when processing fn throws exception"
    (let [fail-queue (atom [])]
      (with-redefs [client (constantly nil)
                    sqs/create-queue (constantly nil)
                    sqs/polling-receive (constantly [{:body "msg 1"}
                                                     {:body "msg 2"}])
                    sqs/delete (constantly nil)
                    sqs/send (fn [client q msg] (swap! fail-queue conj msg))]
        @(consume-messages
          nil nil nil #(throw (Exception. (str (:body %) " failed"))))
        (is (= ["{:body \"msg 1\", :error \"msg 1 failed\"}"
                "{:body \"msg 2\", :error \"msg 2 failed\"}"]
               @fail-queue)))))

  (testing "can recover from polling-receive errors"
    (let [queue (atom (list (constantly 1)
                            (fn [& _] (throw (RuntimeException. "1")))
                            (constantly 2)
                            (fn [& _] (throw (RuntimeException. "2")))
                            (constantly 3)))
          done (atom [])]
      (with-redefs [client (constantly nil)
                    sqs/create-queue (constantly nil)
                    sqs/polling-receive (fn [& _]
                                          (letfn [(r []
                                                    (lazy-seq
                                                     (when (seq @queue)
                                                       (let [x (first @queue)]
                                                         (swap! queue rest)
                                                         (cons (x) (r))))))]
                                            (r)))
                    sqs/delete (constantly nil)
                    sqs/send (constantly nil)]
        @(consume-messages
          nil nil nil #(swap! done conj %))
        (is (= [1 2 3]
               @done)))))

  (testing "fails after n times"
    (let [queue (atom (concat
                       (map constantly (range 3))
                       (map #(fn [& _]
                               (throw (RuntimeException. (str %))))
                            (range 10))
                       (map constantly (range 3))))
          done (atom [])]
      (with-redefs [client (constantly nil)
                    backoff-start 1 ;; so it doesn't take forever
                    max-failures 10
                    sqs/create-queue (constantly nil)
                    sqs/polling-receive (fn [& _]
                                          (letfn [(r []
                                                    (lazy-seq
                                                     (when (seq @queue)
                                                       (let [x (first @queue)]
                                                         (swap! queue rest)
                                                         (cons (x) (r))))))]
                                            (r)))
                    sqs/delete (constantly nil)
                    sqs/send (constantly nil)]
        @(consume-messages
          nil nil nil #(swap! done conj %))
        (is (= [0 1 2]
               @done))))))
