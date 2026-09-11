(ns distilling.advisor
  "DistillingAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes batch operations based on plant state and
  incoming spirits lots. The advisor is SEALED into the `:advise` node of
  the operation graph; every proposal is routed through the independent
  Governor before committing.

  The advisor makes proposals but has NO direct authority -- every proposal
  it returns carries `:effect :propose` (never a direct write/actuation
  effect) and is always censored by:
    1. Governor (closed op-allowlist, :effect check, batch-registration
       check, proof/ABV, age-statement, tax mark compliance)
    2. Phase gate (rollout stage)
    3. Human operator (for high-stakes actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `wineops.advisor`).")

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with
    :op, :stake, :effect, :value, :cites, :summary, :confidence"))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op subject]} request]
      (case op
        :log-production-batch
        {:op :log-production-batch
         :stake :log-production-batch
         :effect :propose
         :value {:jurisdiction "US"
                 :batch-id subject
                 :spirit-type "bourbon"
                 :action "Log batch into production records"}
         :cites ["27-CFR-5.22-TTB-Spirits-Regulations"]
         :summary "Batch proof/ABV verified; aging requirements met; tax mark applied; bottle label approved"
         :confidence 0.85}

        :coordinate-shipment
        {:op :coordinate-shipment
         :stake :coordinate-shipment
         :effect :propose
         :value {:batch-id subject
                 :destination "Distributor warehouse"
                 :transport-mode "temperature-controlled"}
         :cites ["27-CFR-5.35-TTB-Shipping-Records"]
         :summary "Final product proof verified; excise compliance confirmed; ready for shipment"
         :confidence 0.82}

        :flag-food-safety-concern
        {:op :flag-food-safety-concern
         :stake :monitoring
         :effect :propose
         :value {:batch-id subject
                 :concern-type "methanol-cut-timing"
                 :description "Foreshots (methanol-rich heads) cut timing deviated from the still run's proof-based cut schedule -- possible contamination risk"
                 :recommended-action "hold-for-lab-verification"}
         :cites ["27-CFR-5.28-TTB-Proof-Determination"]
         :summary "Detected a food-safety-relevant production anomaly; escalating for human re-verification"
         :confidence 0.70}

        :schedule-maintenance
        {:op :schedule-maintenance
         :stake :monitoring
         :effect :propose
         :value {:equipment "still-5a"
                 :maintenance-type "proof-gauge-certification"
                 :scheduled-date "2026-08-15"}
         :cites ["27-CFR-5.14-TTB-Equipment-Maintenance"]
         :summary "Routine proof gauge recalibration scheduled"
         :confidence 0.95}

        {:op op
         :stake :monitoring
         :effect :propose
         :value {:batch-id subject}
         :cites []
         :summary "Unknown operation"
         :confidence 0.5}))))

(defn mock-advisor
  "Create a mock advisor for testing."
  []
  (MockAdvisor.))

(defn trace
  "Audit trace: record what the advisor proposed."
  [request proposal]
  {:t :advisor-proposed
   :op (:op request)
   :subject (:subject request)
   :disposition :proposed
   :summary (:summary proposal)
   :confidence (:confidence proposal)})
