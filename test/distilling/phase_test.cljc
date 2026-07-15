(ns distilling.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.phase :as phase]))

(deftest verdict-to-disposition
  (testing "hard violation -> :hold"
    (let [verdict {:hard? true :escalate? false}]
      (is (= :hold (phase/verdict->disposition verdict)))))

  (testing "escalate -> :escalate"
    (let [verdict {:hard? false :escalate? true}]
      (is (= :escalate (phase/verdict->disposition verdict)))))

  (testing "clean -> :commit"
    (let [verdict {:hard? false :escalate? false}]
      (is (= :commit (phase/verdict->disposition verdict))))))

(deftest phase-gating
  (testing "phase 0 (sandbox) holds everything"
    (let [request {:op :log-production-batch}
          result (phase/gate 0 request :commit)]
      (is (= :hold (:disposition result)))
      (is (= :phase-0-sandbox (:reason result)))))

  (testing "phase 1 (monitoring) escalates actuation"
    (let [request {:op :log-production-batch}
          result (phase/gate 1 request :commit)]
      (is (= :escalate (:disposition result)))
      (is (= :phase-1-actuation-requires-human (:reason result))))

    (let [request {:op :flag-food-safety-concern}
          result (phase/gate 1 request :commit)]
      (is (= :commit (:disposition result)))))

  (testing "phase 2 allows logging, escalates shipment"
    (let [request {:op :log-production-batch}
          result (phase/gate 2 request :commit)]
      (is (= :commit (:disposition result))))

    (let [request {:op :coordinate-shipment}
          result (phase/gate 2 request :commit)]
      (is (= :escalate (:disposition result)))
      (is (= :phase-2-shipment-escalate (:reason result)))))

  (testing "phase 3 full autonomy"
    (let [request {:op :log-production-batch}
          result (phase/gate 3 request :commit)]
      (is (= :commit (:disposition result))))

    (let [request {:op :coordinate-shipment}
          result (phase/gate 3 request :commit)]
      (is (= :commit (:disposition result))))))

(deftest phase-respects-governor-hold
  (testing "phase 3 still respects governor hard violations"
    (let [request {:op :log-production-batch}
          result (phase/gate 3 request :hold)]
      (is (= :hold (:disposition result))))))
