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
  (testing "batch with proof in range has no proof-related hard violation, but still escalates (log-production-batch is always-high-stakes)"
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
                      :effect :propose
                      :cites ["27-CFR-5.22"]
                      :value {:jurisdiction "US"}
                      :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        ;; :log-production-batch is real actuation (high-stakes) so it
        ;; ALWAYS escalates for human sign-off, even on a fully clean
        ;; batch -- :ok? true would mean silent auto-commit, which this
        ;; actor never does for actuation ops (see `governor/high-stakes`).
        (is (false? (:hard? verdict)))
        (is (not (some #(= :proof-out-of-range (:rule %)) (:violations verdict))))
        (is (false? (:ok? verdict)))
        (is (true? (:escalate? verdict)))
        (is (true? (:high-stakes? verdict))))))

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

(deftest governor-op-not-allowed
  (testing "an out-of-allowlist op (direct distillation-line control) is a hard, permanent block"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (let [request {:op :control-still-line :subject "batch-001"}
            proposal {:effect :propose :cites ["27-CFR-5.22"] :value {:jurisdiction "US"} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

  (testing "an out-of-allowlist op (excise/tax-classification authority) is a hard, permanent block"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (let [request {:op :reclassify-excise-tax-category :subject "batch-001"}
            proposal {:effect :propose :cites ["27-CFR-5.22"] :value {:jurisdiction "US"} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :op-not-allowed (:rule %)) (:violations verdict)))))))

(deftest governor-effect-not-propose
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (let [request {:op :schedule-maintenance :subject "batch-001"}
            proposal {:effect :commit :cites ["27-CFR-5.14"] :value {} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (true? (:hard? verdict)))
        (is (some #(= :effect-not-propose (:rule %)) (:violations verdict))))))

  (testing "a proposal with no :effect key at all is not penalized by this rule"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (let [request {:op :schedule-maintenance :subject "batch-001"}
            proposal {:cites ["27-CFR-5.14"] :value {} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (not (some #(= :effect-not-propose (:rule %)) (:violations verdict))))))))

(deftest governor-batch-not-registered
  (testing "log-production-batch against an unregistered batch is a hard violation"
    (let [st (store/create-mem-store)
          request {:op :log-production-batch :subject "batch-ghost"}
          proposal {:effect :propose :cites ["27-CFR-5.22"] :value {:jurisdiction "US"} :confidence 0.9}
          verdict (governor/check request {} proposal st)]
      (is (true? (:hard? verdict)))
      (is (some #(= :batch-not-registered (:rule %)) (:violations verdict)))))

  (testing "schedule-maintenance against an unregistered batch is also a hard violation"
    (let [st (store/create-mem-store)
          request {:op :schedule-maintenance :subject "batch-ghost"}
          proposal {:effect :propose :cites [] :value {} :confidence 0.9}
          verdict (governor/check request {} proposal st)]
      (is (true? (:hard? verdict)))
      (is (some #(= :batch-not-registered (:rule %)) (:violations verdict)))))

  (testing "a registered batch does not trigger this rule"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0})
      (let [request {:op :schedule-maintenance :subject "batch-001"}
            proposal {:effect :propose :cites [] :value {} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (not (some #(= :batch-not-registered (:rule %)) (:violations verdict))))))))

(deftest governor-abv-out-of-tolerance
  (testing "measured ABV (from proof) matching declared ABV within jurisdiction tolerance passes"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon"
                        :proof-us 80.0            ;; 80 US proof == 40.0% ABV
                        :declared-abv 40.0         ;; matches exactly
                        :jurisdiction "US"})
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:effect :propose :cites ["27-CFR-5.37"] :value {:jurisdiction "US"} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (not (some #(= :abv-out-of-tolerance (:rule %)) (:violations verdict)))))))

  (testing "measured ABV (from proof) far outside declared ABV's tolerance band is a hard violation"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001"
                       {:spirit-type "bourbon"
                        :proof-us 100.0            ;; 100 US proof == 50.0% ABV
                        :declared-abv 40.0          ;; label says 40.0% -- 10-point deviation
                        :jurisdiction "US"})        ;; US tolerance is +/-0.5%
      (let [request {:op :log-production-batch :subject "batch-001"}
            proposal {:effect :propose :cites ["27-CFR-5.37"] :value {:jurisdiction "US"} :confidence 0.9}
            verdict (governor/check request {} proposal st)]
        (is (true? (:hard? verdict)))
        (is (some #(= :abv-out-of-tolerance (:rule %)) (:violations verdict)))))))

(deftest governor-flag-food-safety-concern-always-escalates
  (testing "a clean flag-food-safety-concern proposal is never auto-ok"
    (let [st (store/create-mem-store)]
      (store/add-batch st "batch-001" {:spirit-type "bourbon" :proof-us 100.0 :jurisdiction "US"})
      (let [request {:op :flag-food-safety-concern :subject "batch-001"}
            proposal {:effect :propose :cites ["27-CFR-5.28"] :value {:jurisdiction "US"} :confidence 0.99}
            verdict (governor/check request {} proposal st)]
        (is (false? (:ok? verdict)))
        (is (false? (:hard? verdict)))
        (is (true? (:escalate? verdict)))))))
