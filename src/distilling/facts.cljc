(ns distilling.facts
  "Reference facts for spirits distilling: jurisdiction requirements for
  batch distillation, proof/ABV compliance, age statements, excise-tax
  marking, and labeling. This namespace contains pure lookup functions for
  regulatory compliance checks -- the Governor calls these to validate
  proposals against jurisdiction requirements."
  (:require [clojure.set :as set]))

(def jurisdictions
  "Spirits distilling jurisdictions and their required documentation/evidence
  checklist requirements. Excise tax, age statements, and proof compliance
  are jurisdiction-specific."
  {"US"
   {:id "US"
    :name "United States (TTB/Alcohol and Tobacco Tax and Trade Bureau)"
    :proof-standard 100.0                          ; minimum proof requirement
    :abv-tolerance-pct 0.5
    :age-statement-minimum-years 2                 ; for 'Bourbon', 'Scotch' etc.
    :required-evidence
    [:distillation-log               ; still-run records, dates, spirits proof
     :proof-gauge-certification      ; ABV/proof measurement certification
     :barrel-code-records            ; barrel ID and aging duration
     :tax-stamp-verification         ; excise tax compliance
     :bottle-label-approval          ; TTB label approval cert
     :production-report]}

   "JP"
   {:id "JP"
    :name "日本 (酒税法 / 国税庁)"
    :proof-standard 20.0                           ; minimum proof for shochu/spirits
    :abv-tolerance-pct 0.5
    :age-statement-minimum-years 3                 ; for aged shochu
    :required-evidence
    [:distillation-log
     :proof-gauge-certification
     :barrel-code-records
     :liquor-tax-mark
     :bottle-label-approval
     :production-report]}

   "EU"
   {:id "EU"
    :name "European Union (Spirit Regulation 1601/2009)"
    :proof-standard 15.0
    :abv-tolerance-pct 0.5
    :age-statement-minimum-years 2                 ; for age-statement categories
    :required-evidence
    [:distillation-log
     :proof-gauge-certification
     :barrel-code-records
     :tax-compliance-mark
     :bottle-label-approval
     :production-report
     :geographical-indication-if-claimed]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that all required-evidence items are present in the batch's
  checklist. Returns true only if every item in the jurisdiction's
  required-evidence list is present in the batch's checklist."
  [jurisdiction-id checklist]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (if-not j
      false
      (let [required (set (:required-evidence j))
            present (set checklist)]
        (set/subset? required present)))))

(def spirit-types
  "Valid spirit product categories and their regulatory/proof requirements."
  {"bourbon"
   {:id "bourbon"
    :name "ボーボン"
    :proof-min 80.0
    :proof-max 190.0
    :abv-min-pct 40.0
    :age-statement-required? true
    :age-statement-minimum-years 2
    :barrel-type "new charred oak"}

   "scotch"
   {:id "scotch"
    :name "スコッチ"
    :proof-min 80.0
    :proof-max 190.0
    :abv-min-pct 40.0
    :age-statement-required? true
    :age-statement-minimum-years 3
    :barrel-type "ex-bourbon or ex-sherry"}

   "vodka"
   {:id "vodka"
    :name "ウォッカ"
    :proof-min 80.0
    :proof-max 190.0
    :abv-min-pct 40.0
    :age-statement-required? false
    :filtration-required? true}

   "gin"
   {:id "gin"
    :name "ジン"
    :proof-min 80.0
    :proof-max 190.0
    :abv-min-pct 37.5
    :age-statement-required? false
    :botanical-requirement true}

   "shochu"
   {:id "shochu"
    :name "焼酎"
    :proof-min 20.0
    :proof-max 145.0
    :abv-min-pct 20.0
    :age-statement-required? false}})

(defn spirit-type-by-id [id]
  (get spirit-types id))
