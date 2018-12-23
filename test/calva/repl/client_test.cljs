(ns calva.repl.client-test
  (:require [cljs.test :include-macros true :refer [deftest is testing]]
            [calva.repl.client :as sut]))

(deftest update-state-test
  (testing "not done"
    (let [state {"id" {:id "id"
                       :results []}}
          expected {"id" {:id "id"
                          :results ["lol"]}}]
      (is (= expected (sut/update-state state {:id "id" :status "lol"})))))
  (testing "done"
    (let [state {"id" {:id "id"
                       :results []}}
          expected {}]
      (is (= expected (sut/update-state state {:id "id" :status "done"}))))))