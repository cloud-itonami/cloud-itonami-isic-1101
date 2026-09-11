(ns distilling.render-html
  "Build-time HTML renderer. Drives the REAL actor stack deterministically."
  (:require [kotoba.lang.text :as str]
            [distilling.store :as store]
            [distilling.operation :as op]
            [langgraph.graph :as g]))

(defn- exec! [actor tid request ctx] (g/run* actor {:request request :context ctx} {:thread-id tid}))
(defn- approve! [actor tid by] (g/run* actor {:approval {:status :approved :by by}} {:thread-id tid :resume? true}))
(defn- reject! [actor tid by] (g/run* actor {:approval {:status :rejected :by by}} {:thread-id tid :resume? true}))

(def ^:private ctx2 {:actor-id "distilling-wave3-001" :phase 2})
(def ^:private ctx1 {:actor-id "distilling-wave3-001" :phase 1})
(def ^:private ctx0 {:actor-id "distilling-wave3-001" :phase 0})

(defn run-demo! []
  (let [db (store/create-mem-store) actor (op/build db)]
    (exec! actor "t1" {:op :log-production-batch :subject "batch-001"} ctx2)
    (approve! actor "t1" "master-distiller-01")
    (exec! actor "t2" {:op :schedule-maintenance :subject "batch-001"} ctx1)
    (exec! actor "t3" {:op :schedule-maintenance :subject "batch-001"} ctx0)
    (approve! actor "t3" "master-distiller-01")
    (exec! actor "t4" {:op :log-production-batch :subject "batch-999"} ctx2)
    db))

(defn- esc [v] (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- last-fact-for [ledger bid] (last (filter #(= (:subject %) bid) ledger)))
(defn- status-cell [ledger bid]
  (let [f (last-fact-for ledger bid)]
    (cond (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved</span>"
      (= :governor-hold (:t f)) (let [rule (-> f :basis first)] (str "<span class=\"critical\">HARD hold: " (esc (str (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))
(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (str t)) (esc (str (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map str) (str/join ", ")) (some-> disposition str) ""))))
(def ^:private gate-rows
  ["        <tr><td><code>:log-production-batch</code></td><td><span class=\"warn\">ALWAYS human approval (master distiller)</span></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"ok\">phase-1+ auto-commit when clean; phase-0 escalates</span></td></tr>"
   "        <tr><td><code>:proof-spirit-batch</code></td><td><span class=\"warn\">ALWAYS human approval; ABV range check</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">ALWAYS human approval (actuation)</span></td></tr>"])
(defn render [db]
  (let [ledger (vec (store/ledger db))
        lrows (str/join "\n" (map ledger-row ledger))]
    (str "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-1101</title>"
     "<style>body{font:14px/1.5 sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#3a1a0a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".muted{color:#777;font-size:.82rem}table{border-collapse:collapse;width:100%;font-size:.85rem}"
     "th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}"
     "code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}</style></head><body>"
     "<header class=\"bar\"><h1>Distilling ops (ISIC 1101) — <code>distilling</code></h1></header><main>"
     "<section class=\"card\"><h2>Spirit batches</h2>"
     "<p class=\"muted\">Demo from <code>distilling.store</code> via <code>distilling.render-html</code>. No invented data.</p>"
     "<table><thead><tr><th>Batch</th><th>Status</th></tr></thead><tbody>"
     "<tr><td>batch-001</td><td>" (status-cell ledger "batch-001") "</td></tr>"
     "<tr><td>batch-999</td><td>" (status-cell ledger "batch-999") "</td></tr>"
     "</tbody></table></section>"
     "<section class=\"card\"><h2>Action gate</h2>"
     "<table><thead><tr><th>Op</th><th>Gate</th></tr></thead><tbody>" (str/join "\n" gate-rows) "</tbody></table></section>"
     "<section class=\"card\"><h2>Audit ledger</h2>"
     "<table><thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead><tbody>" lrows "</tbody></table></section>"
     "</main></body></html>")))
(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) f (java.io.File. out)]
    (.. f getParentFile mkdirs) (spit f (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
