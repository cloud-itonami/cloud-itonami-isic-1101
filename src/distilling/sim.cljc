(ns distilling.sim
  "Demo / simulation: run a sample spirits-distilling workflow through the
  OperationActor. Exercises the advisor -> governor -> phase gate -> commit flow."
  (:require [distilling.operation :as op]
            [distilling.store :as store]))

(defn -main
  "Run a demo batch-logging workflow."
  [& _args]
  (let [s (store/create-mem-store)
        subject "batch-bourbon-2026-Q3-001"
        _ (store/add-batch s subject
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
        context {:actor-id "distilling-wave3-001" :phase 2}
        request {:op :log-production-batch :subject subject}
        graph (op/build s {})]
    (println "=== Spirits Distilling OperationActor Demo ===")
    (println "Request:" request)
    (let [result (graph request context)]
      (println "Disposition:" (:disposition result))
      (println "Audit:" (mapv (fn [fact] (dissoc fact :violations)) (:audit result)))
      (println "Verdict confidence:" (-> result :verdict :confidence))
      (if (:record result)
        (println "Record committed:" (:record result))
        (println "No record committed (hold or escalate)")))))
