(ns distilling.registry
  "Compliance registry for spirits distilling. Pure functions for validating:
  - Proof/ABV within regulatory bounds per spirit type
  - Age statements (when required, minimum aging period met)
  - Tax mark / excise compliance (US TTB, JP 酒税法, EU)
  - Labeling/bottle approval
  - Distillation/production records completeness
  These are policy checks enforced unconditionally by the Governor."
  (:require [clojure.string :as str]))

;; ===== Proof/ABV compliance =====

(defn proof-out-of-range?
  "Check if a spirit batch's proof (US proof scale, 0-200) falls outside the
  type's requirements. US proof = ABV * 2, so 80 proof = 40% ABV."
  [measured-proof-us proof-min proof-max]
  (or (< measured-proof-us proof-min)
      (> measured-proof-us proof-max)))

(defn abv-out-of-tolerance?
  "Check if measured ABV deviates beyond jurisdiction tolerance (typically 0.5%)."
  [measured-abv declared-abv tolerance-pct]
  (> (abs (- measured-abv declared-abv)) tolerance-pct))

;; ===== Age statement compliance =====

(defn age-statement-insufficient?
  "For aged spirits (bourbon, scotch, etc.), verify that the minimum aging
  period (per jurisdiction + spirit type) has been satisfied. Input: number
  of months barrel-aged. Returns true if minimum is NOT met."
  [aging-months-completed minimum-years]
  (let [minimum-months (* minimum-years 12)]
    (< aging-months-completed minimum-months)))

;; ===== Tax mark compliance =====

(defn tax-mark-missing?
  "Check if the excise tax mark (US TTB strip stamp, JP 酒税法 mark, etc.)
  has been applied. tax-mark-applied? is a boolean."
  [tax-mark-applied?]
  (not (true? tax-mark-applied?)))

;; ===== Labeling compliance =====

(defn bottle-label-not-approved?
  "Check if the bottle label has NOT been approved by jurisdiction authority
  (TTB for US, 酒類管理研究家 for JP, etc.)."
  [label-approved?]
  (not (true? label-approved?)))

;; ===== Production record completeness =====

(defn production-record-incomplete?
  "Check if the batch's production/distillation log is missing required fields
  for audit trail: still-run date, proof measured, barrel code, etc."
  [production-record required-keys]
  (not (every? #(contains? production-record %) required-keys)))

;; ===== Compliance utilities =====

(defn abs [x]
  (if (neg? x) (- x) x))

(defn verify-batch-proof-compliance
  "Comprehensive proof/ABV check. Returns {:compliant? bool :issues []}."
  [measured-proof-us declared-abv type-min-proof type-max-proof tolerance-pct]
  (let [measured-abv (/ measured-proof-us 2.0)
        issues (cond-> []
                 (proof-out-of-range? measured-proof-us type-min-proof type-max-proof)
                 (conj {:rule :proof-out-of-range
                        :detail (str "Proof " measured-proof-us
                                     " US is outside range [" type-min-proof
                                     ", " type-max-proof "]")})
                 (abv-out-of-tolerance? measured-abv declared-abv tolerance-pct)
                 (conj {:rule :abv-tolerance-exceeded
                        :detail (str "Measured ABV " measured-abv
                                     "% exceeds tolerance from declared "
                                     declared-abv "%")}))]
    {:compliant? (empty? issues) :issues issues}))
