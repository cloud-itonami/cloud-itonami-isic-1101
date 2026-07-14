(ns distilling.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.registry :as registry]))

(deftest proof-compliance
  (testing "proof in range passes"
    (is (false? (registry/proof-out-of-range? 100.0 80.0 190.0))))

  (testing "proof too low fails"
    (is (true? (registry/proof-out-of-range? 60.0 80.0 190.0))))

  (testing "proof too high fails"
    (is (true? (registry/proof-out-of-range? 200.0 80.0 190.0)))))

(deftest abv-tolerance
  (testing "ABV within tolerance passes"
    (is (false? (registry/abv-out-of-tolerance? 40.2 40.0 0.5))))

  (testing "ABV exceeds tolerance fails"
    (is (true? (registry/abv-out-of-tolerance? 40.8 40.0 0.5)))))

(deftest age-statement
  (testing "sufficient aging months passes"
    (is (false? (registry/age-statement-insufficient? 36 2))))  ;; 36 months >= 24 months minimum

  (testing "insufficient aging months fails"
    (is (true? (registry/age-statement-insufficient? 12 2))))  ;; 12 months < 24 months minimum

  (testing "exactly minimum passes"
    (is (false? (registry/age-statement-insufficient? 24 2)))))

(deftest tax-mark-validation
  (testing "tax mark applied passes"
    (is (false? (registry/tax-mark-missing? true))))

  (testing "tax mark missing fails"
    (is (true? (registry/tax-mark-missing? false))))

  (testing "nil tax mark missing fails"
    (is (true? (registry/tax-mark-missing? nil)))))

(deftest label-approval
  (testing "label approved passes"
    (is (false? (registry/bottle-label-not-approved? true))))

  (testing "label not approved fails"
    (is (true? (registry/bottle-label-not-approved? false)))))

(deftest production-record-completeness
  (testing "record with all required keys passes"
    (is (false? (registry/production-record-incomplete?
                 {:still-run-date "2026-07-01"
                  :proof-measured 100.0
                  :barrel-code "B001"
                  :distiller-notes "Clean run"}
                 [:still-run-date :proof-measured :barrel-code :distiller-notes]))))

  (testing "record missing key fails"
    (is (true? (registry/production-record-incomplete?
                {:still-run-date "2026-07-01"
                 :proof-measured 100.0}
                [:still-run-date :proof-measured :barrel-code :distiller-notes]))))

  (testing "empty record fails"
    (is (true? (registry/production-record-incomplete?
                {}
                [:still-run-date])))))

(deftest batch-proof-compliance
  (testing "compliant batch passes"
    (let [result (registry/verify-batch-proof-compliance 100.0 40.0 80.0 190.0 0.5)]
      (is (true? (:compliant? result)))
      (is (empty? (:issues result)))))

  (testing "proof out of range detected"
    (let [result (registry/verify-batch-proof-compliance 60.0 30.0 80.0 190.0 0.5)]
      (is (false? (:compliant? result)))
      (is (some #(= :proof-out-of-range (:rule %)) (:issues result)))))

  (testing "abv tolerance exceeded detected"
    (let [result (registry/verify-batch-proof-compliance 100.0 40.0 80.0 190.0 0.2)]
      (is (false? (:compliant? result)))
      (is (some #(= :abv-tolerance-exceeded (:rule %)) (:issues result))))))
