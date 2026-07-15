(ns distilling.governor
  "Spirits Distilling Governor -- the independent compliance layer that earns
  the DistillingAdvisor the right to commit. The LLM has no notion of:
    - Whether a batch's proof/ABV falls within legal bounds per type/jurisdiction
    - Whether a batch's actual ABV stayed within tolerance of its declared
      ABV (risking a federal/jurisdiction excise-tax-class misclassification)
    - Whether an age statement's minimum aging period has been satisfied
    - Whether the batch has the required tax mark/excise compliance clearance
    - Whether the bottle label has been approved by jurisdiction authorities
    - Whether distillation/production records are complete per jurisdiction
    - Whether the distillery/batch record was independently verified and
      registered before any proposal is made against it

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct still/fermentation/blending-line control (NEVER done by this
  actor -- equipment operation remains exclusive to licensed distillers) OR
  excise/tax-classification-authority decisions (NEVER done by this actor --
  a batch crossing its declared ABV tolerance band is only ever logged and,
  if warranted, flagged; reclassifying the batch's federal/state excise-tax
  category is exclusively a human/tax-authority decision), the Governor
  operates on batch metadata: proof, ABV, aging duration, tax marks, labeling
  approvals. This is plant-operations coordination, not process control and
  not tax administration.

  CRITICAL: Any proposal involving excise-tax/age-statement/proof compliance
  concerns ALWAYS escalates to human operator (master distiller/compliance
  officer) for final sign-off. The LLM's confidence is never sufficient for
  regulatory decisions.

  Hard violations (always HOLD, no override):
    1. Operation outside the closed allowlist (includes any proposal that
       would touch distillation/blending-line control or excise/
       tax-classification-authority decisions)
    2. Proposal asserting an `:effect` other than `:propose`
    3. Distillery/batch record not independently verified/registered before
       any proposal is made against it (applies to every proposal op, not
       only shipment coordination)
    4. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    5. Evidence incomplete (missing required-evidence per jurisdiction)
    6. Proof/ABV out of range (type/jurisdiction bounds violated)
    7. ABV out of the declared batch's tolerance band (excise-tax-class
       misclassification risk)
    8. Age statement insufficient (minimum aging period not met)
    9. Tax mark missing (excise compliance not satisfied)
   10. Bottle label not approved (jurisdiction labeling authority sign-off missing)
   11. Production record incomplete (distillation log missing required fields)
   12. Batch already processed / shipment already finalized (double-commit guards)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-production-batch`, `:coordinate-shipment`)
    - `:flag-food-safety-concern` (never auto-resolved by confidence alone)

  This design mirrors `wineops.governor` (ISIC 1102, verified/promoted) but
  specializes excise-tax/proof/age-statement concerns rather than
  wine-manufacturing-specific food-safety (SO2 residue, contamination,
  sanitation, vintage-percent)."
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

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-food-safety-concern` -- a
  food-safety concern (e.g. methanol-cut timing, contamination) is never
  auto-resolved by advisor confidence alone, it always needs a human look."
  (conj high-stakes :flag-food-safety-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  distillation/blending-line control (still, fermentation, or blending
  equipment operation) or excise/tax-classification-authority decisions
  (e.g. reclassifying a batch's federal/state excise-tax category) -- is a
  hard, permanent block: this actor coordinates plant operations, it does
  not operate equipment and it has no tax-classification authority."
  #{:log-production-batch :schedule-maintenance :flag-food-safety-concern :coordinate-shipment})

;; ----------------------------- checks -----------------------------

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct distillation/blending-line control, or an
  excise/tax-classification-authority decision) is refused unconditionally
  -- this actor has no authority to make such a proposal at all, let alone
  commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-production-batch/"
                  "schedule-maintenance/flag-food-safety-concern/coordinate-shipment) "
                  "に含まれない -- 蒸留/ブレンドライン制御や酒税の税区分判定権限はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- batch-not-registered-violations
  "HARD invariant: a distillery/batch record must be independently
  verified/registered in the store BEFORE ANY proposal (not just shipment
  coordination) can be made against it -- this actor coordinates
  operations for an already-registered batch, it never invents or
  self-registers one from an unverified proposal."
  [{:keys [op subject]} st]
  (when (contains? allowed-ops op)
    (when-not (store/batch-by-id st subject)
      [{:rule :batch-not-registered
        :detail (str subject " は蒸留所に登録されたバッチ記録が無い -- 提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's spirits-compliance requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-production-batch :coordinate-shipment :flag-food-safety-concern}
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

(defn- abv-out-of-tolerance-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's
  measured ABV (derived from `:proof-us`, US proof = ABV * 2) falls within
  the jurisdiction's tolerance band of the batch's declared ABV, via
  `registry/abv-out-of-tolerance?`. Evaluated UNCONDITIONALLY when both
  `:proof-us` and `:declared-abv` are present -- crossing the tolerance
  band risks a federal/jurisdiction excise-tax-class misclassification,
  which this actor never decides on its own."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/batch-by-id st subject)
          j (when b (facts/jurisdiction-by-id (:jurisdiction b)))]
      (when (and b j (:proof-us b) (:declared-abv b)
                 (registry/abv-out-of-tolerance?
                  (/ (:proof-us b) 2.0)
                  (:declared-abv b)
                  (:abv-tolerance-pct j)))
        [{:rule :abv-out-of-tolerance
          :detail (str subject " の実測ABV(" (/ (:proof-us b) 2.0)
                      "%)が申告ABV(" (:declared-abv b)
                      "% ±" (:abv-tolerance-pct j)
                      "%)の許容誤差を外れる -- 酒税区分の誤分類リスクがあり、バッチ登録提案は進められない")}]))))

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
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal -- since the operation being
  proposed (not the advisor's self-reported stake) is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (batch-not-registered-violations request st)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (proof-out-of-range-violations request st)
                           (abv-out-of-tolerance-violations request st)
                           (age-statement-insufficient-violations request st)
                           (tax-mark-missing-violations request st)
                           (label-not-approved-violations request st)
                           (production-record-incomplete-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

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
