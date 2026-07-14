# ADR-0001: Spirits Distilling Actor Architecture

**Status:** Accepted  
**Date:** 2026-07-14  
**Author:** cloud-itonami wave3 batch  

## Summary

The Spirits Distilling Actor (`cloud-itonami-isic-1101`) coordinates plant
operations and regulatory compliance for spirits production. It enforces
proof/ABV bounds, age-statement compliance, excise-tax marks, and labeling
approval per jurisdiction (US TTB, Japan 酒税法, EU 1601/2009). The LLM
advisor proposes; the Governor censors; humans sign off on high-stakes
actions.

## Context

Spirits production is a heavily regulated domain:
- **Proof/ABV** must fall within spirit-type bounds (e.g., bourbon 80-190 US proof).
- **Age statements** require verification of minimum barrel aging (e.g., 2 years for bourbon).
- **Excise marks** (US TTB strip stamp, JP 酒税法 mark) are legally required before shipment.
- **Labeling** must be approved by jurisdiction authorities (TTB, 国税庁, etc.).
- **Production records** (still-run dates, proof measurements, barrel codes) are mandatory audit trail.

Unlike meat processing (food safety: contamination, temperature, sanitation),
spirits compliance is primarily **regulatory** (excise tax, proof, age statement,
labeling). The actor coordinates plant-operator workflow; it does NOT control
distillation, fermentation, or blending (operator-exclusive).

## Design

### Four-layer flow

1. **Advisor** (`distilling.advisor`): LLM proposes batch operation
   (log-production-batch, coordinate-shipment, flag-compliance-concern, etc.)
   with reasoning and confidence.

2. **Governor** (`distilling.governor`): Independent compliance layer enforces
   hard violations (proof out-of-range, tax-mark missing, label not approved,
   production record incomplete, already-processed, etc.). Hard violations
   → HOLD. Low confidence or high-stakes → ESCALATE.

3. **Phase Gate** (`distilling.phase`): Rollout constraint (phase 0-3 sandbox
   → monitoring → production → full autonomy). Phase 0 gates everything.
   Phase 1 escalates actuation. Phase 2 allows logging, escalates shipment.
   Phase 3 is fully autonomous.

4. **Operation** (`distilling.operation`): Orchestrates all four layers in
   a single run. Returns disposition (commit/escalate/hold), audit trail,
   and optional committed record.

### Governor rules (hard violations → HOLD)

- **no-spec-basis**: Proposal cites no jurisdiction (can't verify regulatory requirements).
- **evidence-incomplete**: Batch's evidence checklist (distillation-log, proof-gauge-cert, etc.)
  does not satisfy jurisdiction requirements.
- **proof-out-of-range**: Batch's measured proof/ABV falls outside legal bounds per spirit type.
- **age-statement-insufficient**: Spirit requires age statement, but barrel aging is below minimum.
- **tax-mark-missing**: Excise tax mark not applied.
- **label-not-approved**: Bottle label has not been approved by jurisdiction authority.
- **production-record-incomplete**: Distillation log missing required fields.
- **already-processed**: Batch was already logged into production (idempotency).
- **already-shipment-finalized**: Batch shipment was already finalized (idempotency).

### Soft gates (→ ESCALATE)

- **low-confidence**: Advisor confidence < 60%.
- **high-stakes**: Operations `:log-production-batch`, `:coordinate-shipment` always
  escalate to human operator, even if clean.
- **phase-gate**: Phase constraints (0 sandbox, 1 monitoring escalates actuation,
  2 escalates shipment).

### Store protocol

Swappable backends:
- **MemStore** (development/testing): in-memory map with atom-based state.
- **Datomic / kotoba-server** (production): persistent, queryable, audit-ready.

Current: MemStore only. Production roadmap: Datomic backing.

### Facts

Reference data (`distilling.facts`):
- **Jurisdictions**: US (TTB, 100 proof std), JP (酒税法, 20 proof std), EU (1601/2009, 15 proof std).
- **Spirit types**: bourbon (80-190 proof, 2-year age req'd), vodka, gin, shochu, scotch.
- **Evidence checklists**: per jurisdiction (distillation-log, proof-gauge-cert, barrel-code, tax-mark, label, etc.).

All pure lookup functions; used by Governor for validation.

## Operations

Proposal operations (closed allowlist):

- **`:log-production-batch`**: Batch ready for production logging.
  - Requires: All evidence complete, proof in-range, age-statement satisfied,
    tax-mark applied, label approved, production record complete.
  - Always escalates to human operator (high-stakes).

- **`:coordinate-shipment`**: Batch ready for outbound shipment.
  - Requires: Batch already logged, proof verified, shipment not finalized.
  - Phase 2+ escalates to human. Phase 3 autonomous.

- **`:flag-compliance-concern`**: Raise compliance issue (proof drift, labeling error, etc.).
  - Always escalates to human operator.
  - Used for sensory evaluation hold, re-measurement, etc.

- **`:schedule-maintenance`**: Equipment maintenance (still recalibration, etc.).
  - Low-stakes. Passes through if clean (phase 1+ allows).

## Scope exclusions

This actor does **NOT**:
- Direct still, fermentation, or blending operations (operator-exclusive).
- Certify excise compliance or bottling authorization (human-exclusive).
- Make spirit classification decisions (master distiller-exclusive).
- Measure proof/ABV (that's the operator's job; this actor validates the result).

## Testing

Test suite covers:
- **facts_test**: Jurisdiction lookup, spirit-type lookup, evidence satisfaction.
- **registry_test**: Proof/ABV compliance, age-statement, tax-mark, label, production-record checks.
- **store_test**: MemStore add, retrieve, mark-processed, mark-shipment-finalized.
- **governor_test**: Hard violations (proof, tax-mark, label, evidence, already-processed).
- **phase_test**: Phase 0-3 gating logic.
- **operation_test**: End-to-end flow, audit trail, escalation.

All `.cljc` (portable Clojure). Tests run on JVM via cognitect test-runner.

## Future roadmap

1. **Langgraph integration**: Embed `run-operation` into a langgraph-clj StateGraph
   (today stubbed; langgraph-clj not yet dependency-available for local testing).
2. **Datomic backing**: Swap MemStore for persistent Datomic backend + audit ledger.
3. **Real LLM advisor**: Integrate Claude or similar (today: mock advisor).
4. **Additional jurisdictions**: Extend facts.cljc for more countries (Canada, Taiwan, etc.).
5. **Barrel-tracking subsystem**: Genealogy of barrel provenance across blending operations.

## References

- **ADR-2607121000**: Cloud-Itonami Wave 3 Reverse-Toposort Plan (beverage domain).
- **27 CFR 5.22** (TTB Spirits Regulations).
- **酒税法** (Japan Liquor Tax Act).
- **EU Regulation 1601/2009** (Spirit Drink).
