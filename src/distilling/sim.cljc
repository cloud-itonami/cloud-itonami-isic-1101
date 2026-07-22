(ns distilling.sim
  "Demo driver -- `clojure -M:run` / `clojure -M:dev:run`. Drives the REAL
  compiled `langgraph-clj` `StateGraph` (`distilling.operation/build`)
  end-to-end through a always-escalate batch-logging flow (master
  distiller approves), a low-risk auto-commit (schedule-maintenance), a
  phase-0 sandbox hold, and a HARD-block scenario (proof out of range),
  then prints the resulting audit ledger. Mirrors `transportops.sim`
  (cloud-itonami-isic-869) / `knitwear.sim` (cloud-itonami-isic-1430).

  FIX (this commit): the previous version called `(op/build s {})` and
  invoked the RETURN VALUE AS A PLAIN FUNCTION (`(graph request
  context)`) -- because the old `build` was a hand-rolled stub closure,
  never a real `langgraph.graph` compiled graph. That call shape no
  longer exists; `build` now returns a genuine `CompiledGraph`, driven
  via `langgraph.graph/run*`."
  (:require [langgraph.graph :as g]
            [distilling.operation :as operation]
            [distilling.store :as store]))

(defn scenario [title]
  (println "\n==========================================")
  (println (str "Scenario: " title))
  (println "=========================================="))

(defn- exec [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}}
          {:thread-id tid :resume? true}))

(defn- clean-batch []
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

(defn demo
  "Run the compiled StateGraph through an always-escalating
  batch-logging path (approved by a human master distiller), a
  low-risk auto-commit (maintenance scheduling), a phase-0 sandbox
  hold, and a HARD-block proof-out-of-range scenario; print each
  result and the final audit ledger."
  []
  (println "Spirits Distilling Plant Operations Coordination Actor - Demo")

  (scenario "Always-escalating: log-production-batch (master distiller approves)")
  (let [s (store/create-mem-store)
        subject "batch-bourbon-2026-Q3-001"
        _ (store/add-batch s subject (clean-batch))
        actor (operation/build s)
        held (exec actor "t1" {:op :log-production-batch :subject subject}
                   {:actor-id "distilling-wave3-001" :phase 2})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- master distiller approves --")
    (let [approved (approve! actor "t1" "master-distiller-01")]
      (println "Decision:" (:decision (:state approved)))
      (println "Audit:" (mapv (fn [fact] (dissoc fact :violations)) (:audit (:state approved))))
      (println "Ledger:" (store/ledger s))))

  (scenario "Phase 1: Auto-commit maintenance scheduling (low-risk, no human needed)")
  (let [s (store/create-mem-store)
        subject "batch-bourbon-2026-Q3-002"
        _ (store/add-batch s subject (clean-batch))
        actor (operation/build s)
        result (exec actor "t2" {:op :schedule-maintenance :subject subject}
                     {:actor-id "distilling-wave3-001" :phase 1})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger s)))

  (scenario "Phase 0 (sandbox): clean proposal still held -- nothing auto-commits")
  (let [s (store/create-mem-store)
        subject "batch-bourbon-2026-Q3-003"
        _ (store/add-batch s subject (clean-batch))
        actor (operation/build s)
        result (exec actor "t3" {:op :schedule-maintenance :subject subject}
                     {:actor-id "distilling-wave3-001" :phase 0})]
    (println "Decision:" (:decision (:state result)))
    (println "Audit:" (:audit (:state result))))

  (scenario "HARD-block: proof out of the spirit type's legal range")
  (let [s (store/create-mem-store)
        subject "batch-bourbon-2026-Q3-004"
        _ (store/add-batch s subject (assoc (clean-batch) :proof-us 50.0))
        actor (operation/build s)
        result (exec actor "t4" {:op :log-production-batch :subject subject}
                     {:actor-id "distilling-wave3-001" :phase 3})]
    (println "Decision:" (:decision (:state result)))
    (println "Violations:" (mapv :rule (:violations (first (store/ledger s))))))

  (println "\n==========================================")
  (println "Demo completed successfully")
  (println "=========================================="))

(defn -main [& _args]
  (demo))

(comment
  (demo))
