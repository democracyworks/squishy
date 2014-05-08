(ns democracyworks.squishy-test
  (:require [clojure.test :refer :all]
            [democracyworks.squishy :refer :all :as squishy]
            [cemerick.bandalore :as sqs]))

(deftest consume-messages-test
  (testing "calls function arg on each msg"
    (let [result (atom [])]
      (with-redefs [squishy/get-queue (constantly nil)
                    sqs/delete (constantly nil)
                    sqs/polling-receive
                    (fn [client q & opts]
                      ["msg 1" "msg 2"])]
        (is (= '("MSG 1" "MSG 2")
               (do
                @(consume-messages nil
                                   #(swap! result conj
                                    (clojure.string/upper-case %)))
                @result)))))))
