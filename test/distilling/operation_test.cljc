(ns distilling.operation-test
  "Integration tests for `distilling.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / phase-0-sandbox-hold
  / escalate-approve / escalate-reject / high-stakes-actuation /
  double-commit-guard routes.

  This replaces the previous version of this namespace, which called
  `(op/build s {})` and then invoked the RESULT AS A PLAIN FUNCTION
  (`(graph request context)`) -- because the old `build` was, by its own
  docstring, a \"Stub for building a langgraph-clj StateGraph\" that
  returned a hand-rolled closure with ZERO `langgraph.graph` usage
  anywhere, despite `blueprint.edn` claiming `:itonami.blueprint/maturity
  :implemented`. These tests are FALSIFIABLE on real StateGraph behavior,
  not hardcoded pass strings: the ledger stays empty until a real commit,
  escalated proposals hold-until-approved via a genuine checkpointed
  `interrupt-before`, and a governor rejection blocks commit entirely.
  Mirrors `transportops.operation-test` (cloud-itonami-isic-869) /
  `knitwear.operation-test` (cloud-itonami-isic-1430)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [distilling.operation :as op]
            [distilling.store :as store]))

(defn- exec [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- clean-batch
  "A fully-compliant batch fixture -- proof/ABV in range and matching the
  declared ABV within tolerance, evidence checklist complete, tax mark
  applied, label approved, aged past the minimum, production record
  complete. Mirrors `distilling.sim`'s demo fixture."
  []
  {:spirit-type "bourbon"
   :proof-us 100.0
   :declared-abv 50.0
   :evidence-checklist [:distillation-log :proof-gauge-certification
                        :barrel-code-records :tax-stamp-verification
                        :bottle-label-approval :production-report]
   :jurisdiction "US"
   :aging-months 30
   :tax-mark-applied? true
   :label-approved? true
   :production-record {:still-run-date "2026-07-01"
                       :proof-measured 100.0
                       :barrel-code "B001"
                       :distiller-notes "Clean run, no anomalies"}})

(deftest ledger-starts-empty
  (testing "a freshly created store's audit ledger is empty until a real
            commit lands -- no proposal, no verdict, no graph run has
            happened yet"
    (let [s (store/create-mem-store)]
      (is (empty? (store/ledger s))))))

(deftest commit-path-schedule-maintenance-auto-commits
  (testing ":schedule-maintenance is the ONE op in the closed allowlist
            that is NOT in `governor/always-escalate-ops` -- a clean,
            registered-batch request for it genuinely commits through
            the real compiled graph (no interrupt) and appends exactly
            one fact to the audit ledger"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          result (exec actor "t-commit" {:op :schedule-maintenance :subject "batch-001"}
                       {:actor-id "test-001" :phase 1})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (is (map? (:record state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :schedule-maintenance (:op (first ledger))))
        (is (= "batch-001" (:subject (first ledger))))))))

(deftest hard-hold-path-proof-out-of-range
  (testing "proof far outside the spirit type's legal range is a HARD,
            permanent governor violation -- the real graph routes
            straight to :hold (no interrupt, no human-approval detour)
            EVEN AT PHASE 3 (full autonomy), and durably records the
            hold fact -- the ledger never gets a :committed fact"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (assoc (clean-batch) :proof-us 50.0))
          actor (op/build s)
          result (exec actor "t-hold" {:op :log-production-batch :subject "batch-001"}
                       {:actor-id "test-001" :phase 3})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (is (false? (:record state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :proof-out-of-range (:rule %)) (:violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest hard-hold-path-unregistered-batch
  (testing "logging a completely unregistered batch ID is also a HARD
            violation, re-derived from the store's own record -- never
            trusted from the proposal"
    (let [s (store/create-mem-store)
          actor (op/build s)
          result (exec actor "t-hold-unreg" {:op :log-production-batch :subject "batch-ghost"}
                       {:actor-id "test-001" :phase 3})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :batch-not-registered (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest phase-0-sandbox-holds-everything
  (testing "phase 0 (sandbox) holds EVEN a governor-clean, non-escalating
            :schedule-maintenance proposal -- the phase gate overrides
            the governor's own :commit verdict, distinguished in the
            ledger by :phase-reason :phase-0-sandbox"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          result (exec actor "t-phase0" {:op :schedule-maintenance :subject "batch-001"}
                       {:actor-id "test-001" :phase 0})
          state (:state result)]
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (= :phase-0-sandbox (:phase-reason (first ledger))))
        (is (empty? (:violations (first ledger))))))))

(deftest escalate-then-approve-commits-log-production-batch
  (testing ":log-production-batch is real actuation
            (`governor/always-escalate-ops`) -- ALWAYS escalates even on
            a fully clean, phase-2 batch. The real graph GENUINELY
            interrupts (checkpointed) at :request-approval; the ledger
            stays EMPTY until a human master distiller approve! resumes
            the SAME compiled graph and commits via the graph's own
            :request-approval -> :commit edge"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          held (exec actor "t-escalate" {:op :log-production-batch :subject "batch-001"}
                     {:actor-id "test-001" :phase 2})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- ledger stays empty
          until a human signs off")
      (let [approved (g/run* actor {:approval {:status :approved :by "master-distiller-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:decision approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :log-production-batch (:op (first ledger))))
          (is (= "master-distiller-01" (:approved-by (first ledger)))))))))

(deftest escalate-then-reject-holds
  (testing "a human compliance officer rejecting an escalated request
            routes to :hold via the :request-approval node's own
            decision -- governor rejection blocks commit"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          _held (exec actor "t-reject" {:op :log-production-batch :subject "batch-001"}
                      {:actor-id "test-001" :phase 2})
          rejected (g/run* actor {:approval {:status :rejected :by "compliance-officer-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest high-stakes-shipment-always-escalates-then-commits
  (testing "shipment coordination is a high-stakes actuation
            (`governor/high-stakes`) -- ALWAYS escalates even when the
            underlying batch is fully verified/clean; approval resumes
            the SAME compiled graph"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          held (exec actor "t-shipment" {:op :coordinate-shipment :subject "batch-001"}
                     {:actor-id "test-001" :phase 3})]
      (is (= :interrupted (:status held)))
      (is (empty? (store/ledger s)))
      (let [approved (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
                             {:thread-id "t-shipment" :resume? true})]
        (is (= :commit (:decision (:state approved))))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :coordinate-shipment (:op (first ledger)))))))))

(deftest never-auto-commit-holds-at-every-phase-for-food-safety-concern
  (testing "even at phase 3 (full autonomy for what CAN auto-commit),
            :flag-food-safety-concern never auto-commits -- it always
            interrupts, proven against the real graph"
    (doseq [phase-num [1 2 3]]
      (let [s (store/create-mem-store)
            _ (store/add-batch s "batch-001" (clean-batch))
            actor (op/build s)
            held (exec actor (str "t-safety-" phase-num)
                       {:op :flag-food-safety-concern :subject "batch-001"}
                       {:actor-id "test-001" :phase phase-num})]
        (is (= :interrupted (:status held))
            (str "phase " phase-num " should still interrupt for a food-safety concern"))))
    (testing "at phase 0, the sandbox gate overrides escalation into an
              outright hold (no interrupt) -- a stricter outcome, not a
              looser one"
      (let [s (store/create-mem-store)
            _ (store/add-batch s "batch-001" (clean-batch))
            actor (op/build s)
            result (exec actor "t-safety-0" {:op :flag-food-safety-concern :subject "batch-001"}
                         {:actor-id "test-001" :phase 0})]
        (is (= :done (:status result)))
        (is (= :hold (:decision (:state result))))))))

(deftest already-processed-batch-blocks-second-commit
  (testing "a batch already marked processed cannot be logged a second
            time -- the double-commit guard is a HARD violation that
            blocks commit entirely, never reaching :request-approval"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          _ (store/mark-batch-processed s "batch-001")
          actor (op/build s)
          result (exec actor "t-double-commit" {:op :log-production-batch :subject "batch-001"}
                       {:actor-id "test-001" :phase 2})]
      (is (= :done (:status result)))
      (is (= :hold (:decision (:state result))))
      (is (some #(= :already-processed (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest already-finalized-shipment-blocks-second-commit
  (testing "a shipment already marked finalized cannot be coordinated a
            second time -- HARD violation, blocks commit entirely"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          _ (store/mark-shipment-finalized s "batch-001")
          actor (op/build s)
          result (exec actor "t-double-ship" {:op :coordinate-shipment :subject "batch-001"}
                       {:actor-id "test-001" :phase 3})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :already-shipment-finalized (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest audit-trail-includes-advisor-proposal
  (testing "the audit trail always starts with the advisor's own
            proposal trace, whatever the eventual disposition"
    (let [s (store/create-mem-store)
          _ (store/add-batch s "batch-001" (clean-batch))
          actor (op/build s)
          result (exec actor "t-audit" {:op :schedule-maintenance :subject "batch-001"}
                       {:actor-id "test-001" :phase 0})
          audit (:audit (:state result))]
      (is (= 2 (count audit)))
      (is (= :advisor-proposed (:t (first audit))))
      (is (= :governor-hold (:t (second audit)))))))
