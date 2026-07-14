(ns distilling.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.operation :as op]
            [distilling.store :as store]))

(deftest operation-flow-clean-batch
  (testing "clean batch in phase 2 -> escalate (high-stakes)"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001"
                             {:spirit-type "bourbon"
                              :proof-us 100.0
                              :evidence-checklist [:distillation-log :proof-gauge-certification
                                                  :barrel-code-records :tax-stamp-verification
                                                  :bottle-label-approval :production-report]
                              :jurisdiction "US"
                              :tax-mark-applied? true
                              :label-approved? true
                              :production-record {:still-run-date "2026-07-01"
                                                 :proof-measured 100.0
                                                 :barrel-code "B001"
                                                 :distiller-notes "Clean"}})
          request {:op :log-production-batch :subject "batch-001"}
          context {:actor-id "test-001" :phase 2}
          graph (op/build s {})
          result (graph request context)]
      (is (= :escalate (:disposition result)))
      (is (seq (:audit result))))))

(deftest operation-flow-with-violations
  (testing "batch with violations -> hold"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001"
                             {:spirit-type "bourbon"
                              :proof-us 50.0  ;; TOO LOW
                              :evidence-checklist [:distillation-log]
                              :jurisdiction "US"})
          request {:op :log-production-batch :subject "batch-001"}
          context {:actor-id "test-001" :phase 2}
          graph (op/build s {})
          result (graph request context)]
      (is (= :hold (:disposition result)))
      (is (false? (:record result))))))

(deftest operation-phase-gating
  (testing "phase 0 sandbox -> always hold"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" {:proof-us 100.0})
          request {:op :log-production-batch :subject "batch-001"}
          context {:actor-id "test-001" :phase 0}
          graph (op/build s {})
          result (graph request context)]
      (is (= :hold (:disposition result))))))

(deftest operation-audit-trail
  (testing "audit trail includes advisor proposal and disposition"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" {:proof-us 100.0})
          request {:op :log-production-batch :subject "batch-001"}
          context {:actor-id "test-001" :phase 0}
          graph (op/build s {})
          result (graph request context)
          audit (:audit result)]
      (is (= 2 (count audit)))
      (is (= :advisor-proposed (:t (first audit))))
      (is (or (= :governor-hold (:t (second audit)))
              (= :approval-requested (:t (second audit))))))))
