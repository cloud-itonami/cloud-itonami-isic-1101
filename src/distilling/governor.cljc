(ns distilling.governor
  "Spirits Distilling Governor -- the independent compliance layer that earns
  the DistillingAdvisor the right to commit. The LLM has no notion of:
    - Whether a batch's proof/ABV falls within legal bounds per type/jurisdiction
    - Whether an age statement's minimum aging period has been satisfied
    - Whether the batch has the required tax mark/excise compliance clearance
    - Whether the bottle label has been approved by jurisdiction authorities
    - Whether distillation/production records are complete per jurisdiction

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct still/fermentation-process control (NEVER done by this actor
  -- equipment operation remains exclusive to licensed distillers), the
  Governor operates on batch metadata: proof, ABV, aging duration, tax marks,
  labeling approvals. This is plant-operations coordination, not process
  control.

  CRITICAL: Any proposal involving excise-tax/age-statement/proof compliance
  concerns ALWAYS escalates to human operator (master distiller/compliance
  officer) for final sign-off. The LLM's confidence is never sufficient for
  regulatory decisions.

  Hard violations (always HOLD, no override):
    1. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    2. Evidence incomplete (missing required-evidence per jurisdiction)
    3. Proof/ABV out of range (type/jurisdiction bounds violated)
    4. Age statement insufficient (minimum aging period not met)
    5. Tax mark missing (excise compliance not satisfied)
    6. Bottle label not approved (jurisdiction labeling authority sign-off missing)
    7. Production record incomplete (distillation log missing required fields)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-production-batch`, `:coordinate-shipment`)

  This design mirrors `meatprocessing.governor` but specializes excise-tax/
  proof/age-statement concerns rather than food-safety (contamination,
  sanitation, temperature, time)."
  (:require [distilling.facts :as facts]
            [distilling.registry :as registry]
            [distilling.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into production records (`:log-production-batch`) and
  coordinating shipment of finished product (`:coordinate-shipment`) are the
  two real-world actuation events this actor performs. Both require master
  distiller / compliance officer sign-off."
  #{:log-production-batch :coordinate-shipment})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's spirits-compliance requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-production-batch :coordinate-shipment :flag-compliance-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式specificationの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-production-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(distillation-log/proof-gauge-cert等)が充足していない状態での提案"}]))))

(defn- proof-out-of-range-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the batch's proof/ABV
  falls within type/jurisdiction bounds via `registry/proof-out-of-range?`.
  Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)
          s (when b (facts/spirit-type-by-id (:spirit-type b)))]
      (when (and b s (:proof-us b)
                 (registry/proof-out-of-range?
                  (:proof-us b)
                  (:proof-min s)
                  (:proof-max s)))
        [{:rule :proof-out-of-range
          :detail (str subject " のproof(" (:proof-us b) " US)が許可範囲["
                      (:proof-min s) ", " (:proof-max s) "] の外 -- バッチ登録提案は進められない")}]))))

(defn- age-statement-insufficient-violations
  "For `:log-production-batch`, if the spirit type requires an age statement,
  INDEPENDENTLY verify that minimum aging period has been met via
  `registry/age-statement-insufficient?`. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)
          s (when b (facts/spirit-type-by-id (:spirit-type b)))
          j (when b (facts/jurisdiction-by-id (:jurisdiction b)))]
      (when (and b s j (:age-statement-required? s) (:aging-months b)
                 (registry/age-statement-insufficient?
                  (:aging-months b)
                  (or (:age-statement-minimum-years s)
                      (:age-statement-minimum-years j))))
        [{:rule :age-statement-insufficient
          :detail (str subject " の熟成期間(" (:aging-months b)
                      " 月)が最低要件(年" (or (:age-statement-minimum-years s) 2)
                      ")を満たさない -- バッチ登録提案は進められない")}]))))

(defn- tax-mark-missing-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the batch has the required
  tax mark / excise compliance clearance."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)]
      (when (and b (registry/tax-mark-missing? (:tax-mark-applied? b)))
        [{:rule :tax-mark-missing
          :detail (str subject " の酒税マーク/exciseクリアランスが無い -- バッチ登録提案は進められない")}]))))

(defn- label-not-approved-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the bottle label has been
  approved by jurisdiction authorities (TTB for US, etc.)."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)]
      (when (and b (registry/bottle-label-not-approved? (:label-approved? b)))
        [{:rule :label-not-approved
          :detail (str subject " のボトルラベルが法域当局の承認を得ていない -- バッチ登録提案は進められない")}]))))

(defn- production-record-incomplete-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that distillation/production
  records contain all required fields per jurisdiction."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)
          required-keys [:still-run-date :proof-measured :barrel-code :distiller-notes]]
      (when (and b (:production-record b)
                 (registry/production-record-incomplete? (:production-record b) required-keys))
        [{:rule :production-record-incomplete
          :detail (str subject " の蒸留log/production recordが必要フィールド(still-run-date等)を欠く -- バッチ登録提案は進められない")}]))))

(defn- already-processed-violations
  "For `:log-production-batch`, refuse to process the SAME batch twice, off
  a dedicated `:processed?` fact."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's shipment
  twice."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a DistillingAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (proof-out-of-range-violations request st)
                           (age-statement-insufficient-violations request st)
                           (tax-mark-missing-violations request st)
                           (label-not-approved-violations request st)
                           (production-record-incomplete-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
