(ns distilling.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [distilling.governor :as governor]
            [distilling.store :as store]))

(deftest governor-no-spec-basis
  (testing "proposal without jurisdiction citation is hard violation"
    (let [request {:op :log-production-batch :subject "batch-001"}
          proposal {:op :log-production-batch :cites []}
          st (store/create-mem-store)
          verdict (governor/check request {} proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:hard? verdict)))
      (is (some #(= :no-spec-basis (:rule %)) (:violations verdict))))))

(deftest governor-proof-validation
  (testing "batch with proof in range passes"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
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
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (true? (:ok? verdict)))
        (is (false? (:hard? verdict))))))

  (testing "batch with proof out of range fails"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon"
                        :proof-us 50.0  ;; too low
                        :evidence-checklist [:distillation-log :proof-gauge-certification
                                            :barrel-code-records :tax-stamp-verification
                                            :bottle-label-approval :production-report]
                        :jurisdiction "US"
                        :tax-mark-applied? true
                        :label-approved? true
                        :production-record {:still-run-date "2026-07-01"
                                           :proof-measured 50.0
                                           :barrel-code "B001"
                                           :distiller-notes "Clean"}})
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :proof-out-of-range (:rule %)) (:violations verdict)))))))

(deftest governor-tax-mark-validation
  (testing "batch without tax mark fails"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon"
                        :proof-us 100.0
                        :evidence-checklist [:distillation-log :proof-gauge-certification
                                            :barrel-code-records :tax-stamp-verification
                                            :bottle-label-approval :production-report]
                        :jurisdiction "US"
                        :tax-mark-applied? false  ;; FAIL
                        :label-approved? true
                        :production-record {:still-run-date "2026-07-01"
                                           :proof-measured 100.0
                                           :barrel-code "B001"
                                           :distiller-notes "Clean"}})
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :tax-mark-missing (:rule %)) (:violations verdict)))))))

(deftest governor-label-approval
  (testing "batch without approved label fails"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon"
                        :proof-us 100.0
                        :evidence-checklist [:distillation-log :proof-gauge-certification
                                            :barrel-code-records :tax-stamp-verification
                                            :bottle-label-approval :production-report]
                        :jurisdiction "US"
                        :tax-mark-applied? true
                        :label-approved? false  ;; FAIL
                        :production-record {:still-run-date "2026-07-01"
                                           :proof-measured 100.0
                                           :barrel-code "B001"
                                           :distiller-notes "Clean"}})
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :label-not-approved (:rule %)) (:violations verdict)))))))

(deftest governor-already-processed
  (testing "cannot process same batch twice"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon" :proof-us 100.0})
      (store/mark-batch-processed st "batch-001")
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :already-processed (:rule %)) (:violations verdict)))))))

(deftest governor-high-stakes-escalation
  (testing "high-stakes operation escalates even if clean"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
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
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:op :log-production-batch
                      :stake :log-production-batch  ;; HIGH-STAKES
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (false? (:hard? verdict)))
        (is (true? (:escalate? verdict)))
        (is (true? (:high-stakes? verdict)))))))
