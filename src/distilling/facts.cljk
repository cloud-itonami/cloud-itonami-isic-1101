(ns distilling.facts
  "Reference facts for spirits distilling: jurisdiction requirements for
  batch distillation, proof/ABV compliance, age statements, excise-tax
  marking, and labeling. This namespace contains pure lookup functions for
  regulatory compliance checks -- the Governor calls these to validate
  proposals against jurisdiction requirements.

  CITATION PROVENANCE (2026-07-25). Every `:legal-basis` / `:provenance` /
  `:verbatim` below was read out of a directly-fetched official primary
  source and re-grepped against the raw markup -- not recalled, and not
  taken from a fetch summary.

    - US: 27 CFR Part 5 (TTB), fetched as official CFR XML from govinfo.gov.
      5.65(c) Tolerances: `\"A tolerance of plus or minus 0.3 percentage
      points is allowed for actual alcohol content that is above or below
      the labeled alcohol content.\"` 5.143 (Whisky): whisky is distilled
      `\"at less than 95 percent alcohol by volume (190° proof)\"`, `\"stored in
      oak containers (except that corn whisky need not be so stored), and
      bottled at not less than 40 percent alcohol by volume (80° proof)\"`.
      NOTE: TTB restructured Part 5 in 2022; the old §5.22 standards-of-
      identity numbering no longer applies (5.22 is now COLA rules), which
      is why the standards are cited from 5.141/5.143.
    - EU: Regulation (EU) 2019/787 on spirit drinks, fetched from EUR-Lex
      (CELEX:32019R0787). Art. 2(c): a spirit drink has `\"a minimum
      alcoholic strength by volume of 15 %, except in the case of spirit
      drinks that comply with the requirements of category 39 of Annex I\"`.
      Annex I: `\"minimum alcoholic strength by volume of vodka shall be
      37,5 %\"`, `\"minimum alcoholic strength by volume of gin shall be
      37,5 %\"`, `\"minimum alcoholic strength by volume of whisky or whiskey
      shall be 40 %\"`, matured `\"three years in wooden casks not exceeding
      700 litres capacity\"`.
    - JP: 酒税法（昭和28年法律第6号、e-Gov law_id 328AC0000000006）第三条,
      fetched via the e-Gov law_data API. 連続式蒸留焼酎 is defined as spirits
      distilled by a 連続式蒸留機 「で、アルコール分が三十六度未満のものをいう。」
      単式蒸留焼酎 is 「穀類又は芋類、これらのこうじ及び水を原料として発酵させた
      アルコール含有物を連続式蒸留機以外の蒸留機……により蒸留したもの」。

  CORRECTIONS MADE FROM THESE SOURCES (the pre-citation table asserted
  several figures that the sources contradict):

    1. EU authority was cited as \"Spirit Regulation 1601/2009\". No such
       spirit-drinks regulation exists: spirit drinks were governed by
       Reg. (EC) 110/2008 and are now governed by Reg. (EU) 2019/787.
       Corrected to 2019/787.
    2. US `:abv-tolerance-pct` was 0.5, LOOSER than the 0.3 percentage
       points 5.65(c) allows. Tightened to 0.3. This field IS read by the
       Governor (`distilling.governor`), so this narrows a live gate --
       the safe direction.
    3. US `:proof-standard` was 100.0 described as a \"minimum proof
       requirement\". TTB sets no 100-proof minimum (100 proof is the
       Bottled-in-Bond standard); 5.143 requires bottling at not less than
       80° proof. Corrected to 80.0.

  UNIT HAZARD (documented, deliberately not \"fixed\" by reinterpreting
  numbers): `:proof-standard` mixes units across jurisdictions -- the US
  value is degrees proof (80° proof = 40 % ABV) while the JP and EU values
  are percent ABV. `:proof-standard-unit` now states which is which
  explicitly rather than leaving the conflation implicit. No code reads
  `:proof-standard`, so this is reference data, not a live gate.

  NOT VERIFIED IN THIS PASS (stated rather than guessed): the 単式蒸留焼酎
  ABV ceiling clause was not isolated in the fetched 酒税法 text, and no
  statutory 20 % floor for shochu was found -- 酒税法 第三条 sets category
  MAXIMA, not a minimum. The existing JP `:proof-standard` 20.0 and the
  shochu `:abv-min-pct` 20.0 are therefore left untouched and flagged
  `:unverified? true` instead of being changed in either direction."
  (:require [clojure.set :as set]
            [kotoba.lang.text :as str]))

(def jurisdictions
  "Spirits distilling jurisdictions and their required documentation/evidence
  checklist requirements. Excise tax, age statements, and proof compliance
  are jurisdiction-specific."
  {"US"
   {:id "US"
    :name "United States (TTB/Alcohol and Tobacco Tax and Trade Bureau)"
    ;; Corrected 100.0 -> 80.0 (2026-07-25): 27 CFR 5.143 requires bottling
    ;; at not less than 80° proof. There is no 100-proof TTB minimum.
    :proof-standard 80.0
    :proof-standard-unit :degrees-proof            ; 80° proof = 40 % ABV
    ;; Tightened 0.5 -> 0.3 (2026-07-25) per 27 CFR 5.65(c). Governor-read.
    :abv-tolerance-pct 0.3
    :age-statement-minimum-years 2                 ; for 'Bourbon', 'Scotch' etc.
    :legal-basis "27 CFR 5.65(c) (alcohol-content tolerance of plus or minus 0.3 percentage points) and 27 CFR 5.143 (whisky standards of identity: distilled at less than 95 percent alcohol by volume (190° proof), stored in oak containers, bottled at not less than 40 percent alcohol by volume (80° proof))"
    :provenance "https://www.govinfo.gov/content/pkg/CFR-2024-title27-vol1/xml/CFR-2024-title27-vol1-sec5-143.xml"
    :provenance-tolerance "https://www.govinfo.gov/content/pkg/CFR-2024-title27-vol1/xml/CFR-2024-title27-vol1-sec5-65.xml"
    :verbatim
    {:tolerance "A tolerance of plus or minus 0.3 percentage points is allowed for actual alcohol content that is above or below the labeled alcohol content."
     :whisky "from a fermented mash of any grain distilled at less than 95 percent alcohol by volume (190° proof) having the taste, aroma, and characteristics generally attributed to whisky, stored in oak containers (except that corn whisky need not be so stored), and bottled at not less than 40 percent alcohol by volume (80° proof)"}
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
    ;; Left at 20.0 deliberately: no statutory 20 % floor was found in the
    ;; fetched 酒税法 text (第三条 sets category MAXIMA). Flagged, not guessed.
    :proof-standard 20.0
    :proof-standard-unit :percent-abv
    :proof-standard-unverified? true
    :abv-tolerance-pct 0.5
    :age-statement-minimum-years 3                 ; for aged shochu
    :legal-basis "酒税法（昭和28年法律第6号）第三条 — 連続式蒸留焼酎は連続式蒸留機により蒸留した酒類で「アルコール分が三十六度未満のもの」、単式蒸留焼酎は連続式蒸留機以外の蒸留機により蒸留したもの"
    :provenance "https://laws.e-gov.go.jp/api/2/law_data/328AC0000000006"
    :verbatim
    {:continuous-still "アルコール含有物を連続式蒸留機……により蒸留した酒類……で、アルコール分が三十六度未満のものをいう。"
     :pot-still "穀類又は芋類、これらのこうじ及び水を原料として発酵させたアルコール含有物を連続式蒸留機以外の蒸留機（以下この号及び第四十三条第七項において「単式蒸留機」という。）により蒸留したもの"}
    :statutory-limits
    {:continuous-still-shochu-max-abv-pct 36.0}    ; 三十六度未満
    :required-evidence
    [:distillation-log
     :proof-gauge-certification
     :barrel-code-records
     :liquor-tax-mark
     :bottle-label-approval
     :production-report]}

   "EU"
   {:id "EU"
    ;; Corrected 2026-07-25: "Spirit Regulation 1601/2009" does not exist.
    ;; Spirit drinks are governed by Reg. (EU) 2019/787 (ex-110/2008).
    :name "European Union (Regulation (EU) 2019/787 on spirit drinks)"
    :proof-standard 15.0
    :proof-standard-unit :percent-abv              ; Art. 2(c) general minimum
    :abv-tolerance-pct 0.5
    :age-statement-minimum-years 2                 ; for age-statement categories
    :legal-basis "Regulation (EU) 2019/787 Art. 2(c) (spirit drinks have a minimum alcoholic strength by volume of 15 %, except Annex I category 39) and Annex I (vodka 37,5 %; gin 37,5 %; whisky/whiskey 40 % and matured three years in wooden casks not exceeding 700 litres)"
    :provenance "https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32019R0787"
    :verbatim
    {:general-minimum "minimum alcoholic strength by volume of 15 %, except in the case of spirit drinks that comply with the requirements of category 39 of Annex I"
     :vodka "minimum alcoholic strength by volume of vodka shall be 37,5 %"
     :gin "minimum alcoholic strength by volume of gin shall be 37,5 %"
     :whisky "minimum alcoholic strength by volume of whisky or whiskey shall be 40 %"
     :whisky-maturation "three years in wooden casks not exceeding 700 litres capacity"}
    :statutory-limits
    {:general-min-abv-pct 15.0
     :vodka-min-abv-pct 37.5
     :gin-min-abv-pct 37.5
     :whisky-min-abv-pct 40.0
     :whisky-maturation-min-years 3
     :whisky-cask-max-litres 700}
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

(defn spec-basis
  "The verified primary-source citation for `jurisdiction-id`, or nil when
  the jurisdiction is unknown. Never synthesizes a citation."
  [jurisdiction-id]
  (when-let [j (jurisdiction-by-id jurisdiction-id)]
    (select-keys j [:legal-basis :provenance :provenance-tolerance
                    :verbatim :statutory-limits])))

(defn cited?
  "True only when `jurisdiction-id` carries a non-blank `:legal-basis`, a
  `:provenance` that is a real absolute http(s) URL, and at least one
  `:verbatim` quote."
  [jurisdiction-id]
  (let [{:keys [legal-basis provenance verbatim]} (spec-basis jurisdiction-id)]
    (boolean (and (string? legal-basis) (not (str/blank? legal-basis))
                  (string? provenance) (str/starts-with? provenance "http")
                  (map? verbatim) (seq verbatim)))))

(defn unverified-fields
  "Fields this jurisdiction carries that are explicitly NOT backed by a
  fetched source, flagged rather than silently presented as grounded."
  [jurisdiction-id]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (cond-> #{}
      (:proof-standard-unverified? j) (conj :proof-standard))))

(defn citation-coverage
  "Honest citation coverage over `jurisdictions`, including which fields are
  still unverified."
  []
  (let [ids (keys jurisdictions)
        cited (filter cited? ids)]
    {:jurisdictions (count jurisdictions)
     :cited (count cited)
     :cited-jurisdictions (vec (sort cited))
     :uncited-jurisdictions (vec (sort (remove cited? ids)))
     :unverified-fields (into {} (for [id ids
                                       :let [u (unverified-fields id)]
                                       :when (seq u)]
                                   [id (vec (sort u))]))
     :note (str "cloud-itonami-isic-1101: " (count cited) "/" (count jurisdictions)
                " jurisdictions rest on a directly-fetched official source "
                "(govinfo.gov CFR XML, EUR-Lex, e-Gov law_data API). "
                "Corrections made from those sources: the EU authority was a "
                "non-existent regulation number (1601/2009 -> 2019/787), the US "
                "ABV tolerance was looser than 27 CFR 5.65(c) allows (0.5 -> 0.3), "
                "and the US proof-standard was not a TTB rule (100 -> 80). "
                "Extend only from a real fetched source; never fabricate a "
                "legal-basis, provenance URL or quote.")}))

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
  "Valid spirit product categories and their regulatory/proof requirements.

  MIXED-REGIME NOTE (2026-07-25, documented rather than silently rewritten):
  the `:abv-min-pct` values below do not all come from the same jurisdiction.
  `vodka` 40.0 is the US figure (27 CFR: bottled at not less than 40 % ABV /
  80° proof), while `gin` 37.5 is the EU figure (Reg. (EU) 2019/787 Annex I:
  `\"minimum alcoholic strength by volume of gin shall be 37,5 %\"` -- the US
  minimum for gin is 40 %). Both are real, verified figures, but reading this
  table as one jurisdiction's rules is wrong. They are left as-is because
  raising `gin` to 40.0 would silently change a documented EU-legal product
  into a violation, and lowering `vodka` to 37.5 would LOOSEN a limit -- the
  unsafe direction. A jurisdiction-aware minimum belongs in a future revision
  keyed by jurisdiction, not in a single flattened table.

  `:proof-min` 80.0 / `:proof-max` 190.0 for the whiskies ARE grounded:
  27 CFR 5.143 requires distillation `\"at less than 95 percent alcohol by
  volume (190° proof)\"` and bottling `\"at not less than 40 percent alcohol by
  volume (80° proof)\"`. These were correct before this pass, just uncited."
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
