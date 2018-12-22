(ns calva.repl.client-test
  (:require [cljs.test :include-macros true :refer [deftest is testing]]
            [calva.repl.client :as sut]))

(deftest update-results-test
  (testing "not done"
    (let [results {"id" {:id "id"
                         :results []}}
          expected {"id" {:id "id"
                          :results ["lol"]}}]
      (is (= expected (sut/update-results results "id" "lol" false)))))
  (testing "done"
    (let [results {"id" {:id "id"
                         :results []}}
          expected {}]
      (is (= expected (sut/update-results results "id" "lol" true))))))