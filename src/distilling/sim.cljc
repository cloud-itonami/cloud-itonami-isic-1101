(ns distilling.sim
  "Demo / simulation: run a sample spirits-distilling workflow through the
  OperationActor. Exercises the advisor -> governor -> phase gate -> commit flow."
  (:require [distilling.operation :as op]
            [distilling.store :as store]))

(defn -main
  "Run a demo batch-logging workflow."
  [& _args]
  (let [s (store/create-mem-store)
        context {:actor-id "distilling-wave3-001" :phase 2}
        request {:op :log-production-batch :subject "batch-bourbon-2026-Q3-001"}
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
