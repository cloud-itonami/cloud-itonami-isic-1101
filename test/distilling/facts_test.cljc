(ns distilling.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.facts :as facts]))

(deftest jurisdiction-lookup
  (testing "US jurisdiction"
    (let [us (facts/jurisdiction-by-id "US")]
      (is (= "US" (:id us)))
      (is (= 100.0 (:proof-standard us)))
      (is (contains? (:required-evidence us) :proof-gauge-certification))))

  (testing "JP jurisdiction"
    (let [jp (facts/jurisdiction-by-id "JP")]
      (is (= "JP" (:id jp)))
      (is (= 20.0 (:proof-standard jp)))))

  (testing "unknown jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id "XX")))))

(deftest spirit-type-lookup
  (testing "bourbon"
    (let [bourbon (facts/spirit-type-by-id "bourbon")]
      (is (= "bourbon" (:id bourbon)))
      (is (true? (:age-statement-required? bourbon)))
      (is (= 2 (:age-statement-minimum-years bourbon)))))

  (testing "vodka"
    (let [vodka (facts/spirit-type-by-id "vodka")]
      (is (= "vodka" (:id vodka)))
      (is (false? (:age-statement-required? vodka)))))

  (testing "unknown spirit returns nil"
    (is (nil? (facts/spirit-type-by-id "unknown")))))

(deftest required-evidence-satisfied
  (testing "complete evidence satisfies requirement"
    (is (true? (facts/required-evidence-satisfied?
                "US"
                [:distillation-log :proof-gauge-certification :barrel-code-records
                 :tax-stamp-verification :bottle-label-approval :production-report]))))

  (testing "incomplete evidence fails"
    (is (false? (facts/required-evidence-satisfied?
                 "US"
                 [:distillation-log :proof-gauge-certification]))))

  (testing "unknown jurisdiction returns false"
    (is (false? (facts/required-evidence-satisfied?
                 "XX"
                 [:distillation-log])))))
