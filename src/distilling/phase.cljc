(ns distilling.phase
  "Rollout phase gating. The DistillingActor is deployed in phases:
    Phase 0: Sandbox (logs only, no actuation)
    Phase 1: Low-stakes monitoring (flag-food-safety-concern, schedule-maintenance)
    Phase 2: Production logging (log-production-batch on clean proposals)
    Phase 3: Full autonomy (coordinate-shipment + production logging)

  Each phase gate is idempotent and reversible (phase can be stepped down if
  audit finds issues). This namespace converts Governor verdicts to
  phase-aware dispositions."
  )

(def default-phase 0)

(defn verdict->disposition
  "Convert Governor verdict to base disposition (before phase gating).
  - If Governor said :ok -> :commit
  - If low-confidence or high-stakes -> :escalate
  - If hard violation -> :hold"
  [verdict]
  (cond
    (:hard? verdict) :hold
    (:escalate? verdict) :escalate
    :else :commit))

(defn gate
  "Apply phase constraints. Returns {:disposition ... :reason ...}.
  Phase 0 (sandbox): everything becomes :hold with reason :phase-0-sandbox
  Phase 1 (monitoring): escalate if it's a real-actuation (:log-production-batch, :coordinate-shipment)
  Phase 2: allow log-production-batch, escalate coordinate-shipment
  Phase 3: full autonomy (no phase gates)"
  [phase request base-disposition]
  (let [{:keys [op]} request]
    (case phase
      0 ;; Sandbox: log only
      {:disposition :hold :reason :phase-0-sandbox}

      1 ;; Low-stakes monitoring
      (if (contains? #{:log-production-batch :coordinate-shipment} op)
        {:disposition :escalate :reason :phase-1-actuation-requires-human}
        {:disposition base-disposition})

      2 ;; Production logging allowed, shipment requires human
      (case op
        :coordinate-shipment {:disposition :escalate :reason :phase-2-shipment-escalate}
        {:disposition base-disposition})

      3 ;; Full autonomy
      {:disposition base-disposition}

      ;; Unknown phase defaults to :hold
      {:disposition :hold :reason :unknown-phase})))
