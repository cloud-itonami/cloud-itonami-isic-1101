# ADR-0001: Spirits Distilling Actor Architecture

**Status:** Accepted (amended 2026-07-15 — see "Amendment" below)
**Date:** 2026-07-14 (original), amended 2026-07-15
**Author:** cloud-itonami wave3 batch (original); cloud-itonami stricter-protocol batch (amendment)

## Amendment (2026-07-15)

The original 2026-07-14 scaffold of this actor was reverted from
`kotoba-lang/industry` (`:maturity :spec`, never promoted to
`:implemented`): an independent audit ran `clojure -M:test` directly and
found **4 real test failures** despite the promoting agent's claim that all
tests were green. Root causes (all in test fixtures / production code, not
in the intended design):

1. `facts_test.cljc` used `(contains? (:required-evidence us) ...)` on a
   **vector** -- `contains?` on a vector tests index membership, not
   element membership, so the assertion tested the wrong thing entirely.
2. `operation_test.cljc` expected `(:record result)` to be the literal
   `false` on a HOLD, but `distilling.operation/run-operation` returned
   `nil` (via `when`, not `if`) for any non-`:commit` disposition.
3. `registry_test.cljc`'s "compliant batch passes" fixture called
   `verify-batch-proof-compliance` with `measured-proof-us 100.0` (== 50.0%
   ABV, since US proof = ABV * 2) alongside `declared-abv 40.0` -- a
   self-contradictory batch that always tripped `:abv-tolerance-exceeded`.
   **This was the fixture, not the tolerance-check logic**: the underlying
   `abv-out-of-tolerance?` predicate was (and remains) a correct, symmetric
   `|measured - declared| > tolerance` check.
4. Same root cause as (3) manifesting as the reported "ABV-tolerance logic
   bug" that also blocked the neighboring ISIC 1102 (wine) attempt -- 1102's
   from-scratch rewrite fixed it by using an explicit boundary-comparison
   form (`(or (< x lo) (> x hi))`) instead of an `abs`-based distance check,
   which this repo's `distilling.registry/abv-out-of-tolerance?` now also
   uses, for both clarity and to avoid shadowing `clojure.core/abs`.

Beyond the 4 failing tests, this amendment also closes a **design gap**
found on honest re-review against ISIC 1102 (`wineops.governor`, verified
working): the original Governor had no closed op-allowlist enforcement, no
`:effect :propose` invariant, and no "batch must be independently
registered before any proposal" invariant -- despite the README already
*claiming* the batch-verification invariant. An unregistered/phantom batch
could previously sail through every existing hard check (`evidence-incomplete`,
`proof-out-of-range`, etc. all short-circuit to "no violation" when the
batch record itself is `nil`). All three are now real, tested Governor
rules (`:op-not-allowed`, `:effect-not-propose`, `:batch-not-registered`;
see "Governor rules" below), mirroring `wineops.governor`. The
`:flag-compliance-concern` op was also renamed to `:flag-food-safety-concern`
to match the fleet-wide op-naming convention (ISIC 1102's equivalent) and
now always escalates via `governor/always-escalate-ops`, independent of the
advisor's self-reported `:stake`.

## Summary

The Spirits Distilling Actor (`cloud-itonami-isic-1101`) coordinates plant
operations for spirits production; it is not process control and not a tax
authority. It enforces a closed op-allowlist, an `:effect :propose`-only
invariant, batch-registration, proof/ABV bounds (including ABV-declaration
tolerance), age-statement compliance, excise-tax marks, and labeling
approval per jurisdiction (US TTB, Japan 酒税法, EU 1601/2009). The LLM
advisor proposes; the Governor censors; humans sign off on high-stakes
actions.

## Context

Spirits production is a heavily regulated domain:
- **Proof/ABV** must fall within spirit-type bounds (e.g., bourbon 80-190 US proof).
- **ABV declaration accuracy**: measured ABV must stay within the jurisdiction's
  tolerance of the batch's declared (label) ABV, or the batch risks an
  excise-tax-class misclassification.
- **Age statements** require verification of minimum barrel aging (e.g., 2 years for bourbon).
- **Excise marks** (US TTB strip stamp, JP 酒税法 mark) are legally required before shipment.
- **Labeling** must be approved by jurisdiction authorities (TTB, 国税庁, etc.).
- **Production records** (still-run dates, proof measurements, barrel codes) are mandatory audit trail.

Unlike meat processing or wine manufacturing (food safety: contamination,
temperature, sanitation, SO2), spirits compliance is primarily
**regulatory** (excise tax, proof, age statement, labeling), though
food-safety concerns (methanol-cut timing, contamination) can still arise
and are surfaced via `:flag-food-safety-concern`. The actor coordinates
plant-operator workflow; it does NOT control distillation, fermentation, or
blending (operator-exclusive), and it has no excise/tax-classification
authority (human/tax-authority-exclusive).

## Design

### Four-layer flow

1. **Advisor** (`distilling.advisor`): LLM proposes batch operation
   (log-production-batch, coordinate-shipment, flag-food-safety-concern,
   schedule-maintenance) with reasoning, confidence, and `:effect :propose`.

2. **Governor** (`distilling.governor`): Independent compliance layer enforces
   hard violations (op-not-allowed, effect-not-propose, batch-not-registered,
   proof out-of-range, ABV out-of-tolerance, tax-mark missing, label not
   approved, production record incomplete, already-processed, etc.). Hard
   violations → HOLD. Low confidence or high-stakes/always-escalate op → ESCALATE.

3. **Phase Gate** (`distilling.phase`): Rollout constraint (phase 0-3 sandbox
   → monitoring → production → full autonomy). Phase 0 gates everything.
   Phase 1 escalates actuation. Phase 2 allows logging, escalates shipment.
   Phase 3 is fully autonomous.

4. **Operation** (`distilling.operation`): Orchestrates all four layers in
   a single run. Returns disposition (commit/escalate/hold), audit trail,
   and committed record (or literal `false` when nothing was committed).

### Governor rules (hard violations → HOLD)

- **op-not-allowed**: Proposal's op is outside the closed allowlist (includes
  any proposal that would touch distillation/blending-line control or
  excise/tax-classification-authority decisions).
- **effect-not-propose**: Proposal asserts an `:effect` other than `:propose`.
- **batch-not-registered**: Batch record not independently verified/registered
  in the store before ANY proposal op is evaluated against it.
- **no-spec-basis**: Proposal cites no jurisdiction (can't verify regulatory requirements).
- **evidence-incomplete**: Batch's evidence checklist (distillation-log, proof-gauge-cert, etc.)
  does not satisfy jurisdiction requirements.
- **proof-out-of-range**: Batch's measured proof/ABV falls outside legal bounds per spirit type.
- **abv-out-of-tolerance**: Batch's measured ABV (derived from proof) falls outside the
  jurisdiction's tolerance band of the batch's declared ABV.
- **age-statement-insufficient**: Spirit requires age statement, but barrel aging is below minimum.
- **tax-mark-missing**: Excise tax mark not applied.
- **label-not-approved**: Bottle label has not been approved by jurisdiction authority.
- **production-record-incomplete**: Distillation log missing required fields.
- **already-processed**: Batch was already logged into production (idempotency).
- **already-shipment-finalized**: Batch shipment was already finalized (idempotency).

### Soft gates (→ ESCALATE)

- **low-confidence**: Advisor confidence < 60%.
- **always-escalate-ops**: Operations `:log-production-batch`, `:coordinate-shipment`
  (high-stakes actuation) and `:flag-food-safety-concern` (never auto-resolved by
  confidence alone) always escalate to human operator, even if clean. Determined
  from the REQUEST's `:op`, not the advisor's self-reported `:stake`.
- **phase-gate**: Phase constraints (0 sandbox, 1 monitoring escalates actuation,
  2 escalates shipment).

### Store protocol

Swappable backends:
- **MemStore** (development/testing): in-memory map with atom-based state.
- **Datomic / kotoba-server** (production): persistent, queryable, audit-ready.

Current: MemStore only. Production roadmap: Datomic backing.

### Facts

Reference data (`distilling.facts`):
- **Jurisdictions**: US (TTB, 100 proof std, ±0.5% ABV tolerance), JP (酒税法, 20 proof std),
  EU (1601/2009, 15 proof std).
- **Spirit types**: bourbon (80-190 proof, 2-year age req'd), vodka, gin, shochu, scotch.
- **Evidence checklists**: per jurisdiction (distillation-log, proof-gauge-cert, barrel-code, tax-mark, label, etc.).

All pure lookup functions; used by Governor for validation.

## Operations

Proposal operations (closed allowlist, all `:effect :propose`):

- **`:log-production-batch`**: Batch ready for production logging.
  - Requires: batch registered, all evidence complete, proof in-range, ABV
    within declared tolerance, age-statement satisfied, tax-mark applied,
    label approved, production record complete.
  - Always escalates to human operator (high-stakes).

- **`:coordinate-shipment`**: Batch ready for outbound shipment.
  - Requires: batch registered and already logged, shipment not finalized.
  - Always escalates to human operator (high-stakes).

- **`:flag-food-safety-concern`**: Raise a food-safety concern (e.g.
  methanol-cut timing, contamination, proof drift).
  - Always escalates to human operator, regardless of confidence.

- **`:schedule-maintenance`**: Equipment maintenance (still recalibration, etc.).
  - Requires: batch registered. Low-stakes otherwise; passes through if clean
    (phase 1+ allows).

Any proposal for an operation outside this allowlist -- most importantly
anything that would amount to direct distillation/blending-line control, or
an excise/tax-classification-authority decision -- is refused
unconditionally by the Governor (`:op-not-allowed`).

## Scope exclusions

This actor does **NOT**:
- Direct still, fermentation, or blending operations (operator-exclusive).
- Certify excise compliance or bottling authorization (human-exclusive).
- Reclassify a batch's federal/state excise-tax category (human/tax-authority-exclusive).
- Make spirit classification decisions (master distiller-exclusive).
- Measure proof/ABV (that's the operator's job; this actor validates the result).

## Testing

Test suite covers:
- **facts_test**: Jurisdiction lookup, spirit-type lookup, evidence satisfaction.
- **registry_test**: Proof/ABV compliance, ABV-tolerance, age-statement, tax-mark, label, production-record checks.
- **store_test**: MemStore add, retrieve, mark-processed, mark-shipment-finalized.
- **governor_test**: Hard violations (op-not-allowed, effect-not-propose,
  batch-not-registered, proof, ABV tolerance, tax-mark, label, evidence,
  already-processed) and always-escalate ops.
- **phase_test**: Phase 0-3 gating logic.
- **operation_test**: End-to-end flow, audit trail, escalation.

All `.cljc` (portable Clojure/ClojureScript). Tests run on JVM via
cognitect test-runner (`clojure -M:test`).

## Future roadmap

1. **Langgraph integration**: Embed `run-operation` into a langgraph-clj StateGraph
   (today stubbed; langgraph-clj not yet dependency-available for local testing).
2. **Datomic backing**: Swap MemStore for persistent Datomic backend + audit ledger.
3. **Real LLM advisor**: Integrate Claude or similar (today: mock advisor).
4. **Additional jurisdictions**: Extend facts.cljc for more countries (Canada, Taiwan, etc.).
5. **Barrel-tracking subsystem**: Genealogy of barrel provenance across blending operations.

## References

- **ADR-2607121000**: Cloud-Itonami Wave 3 Reverse-Toposort Plan (beverage domain).
- **cloud-itonami-isic-1102** (`wineops.governor`): verified-working reference
  for the op-allowlist / effect-propose / batch-registered / boundary-form
  ABV-tolerance pattern this amendment adopts.
- **27 CFR 5.22 / 5.37** (TTB Spirits Regulations, proof determination and tolerance).
- **酒税法** (Japan Liquor Tax Act).
- **EU Regulation 1601/2009** (Spirit Drink).
