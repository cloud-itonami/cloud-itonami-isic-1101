# cloud-itonami-isic-1101

Open Business Blueprint for **ISIC 1101**: distilling, rectifying and
blending of spirits — the representative *beverage-manufacturing* (食)
vertical of the 衣食住 scaffold Wave 3 (production/beverage-distilling,
ADR-2607121000).

**Maturity: `:implemented`** — this repository publishes the full
operational actor implementation. The DistillingOps-LLM ⊣ Spirits
Distilling Governor pattern is production-ready.

## What the implemented actor is

**DistillingOps-LLM ⊣ Spirits Distilling Governor** — the fleet-standard
pattern: the advisor LLM drafts batch-logging, production scheduling,
and compliance workflows; the independent `:spirits-distilling-governor`
(a keyword unique fleet-wide) gates every action; plant operations work
is coordinated via plant-operator sign-off, never dispatched directly by
the LLM. Excise-tax/proof/age-statement compliance concerns require
human sign-off.

Operating states: `batch-receipt → quality-check → distillation-log →
proof-verification → tax-mark-application → label-approval → shipment`.

## Scope & Compliance

**This actor supports plant-operations coordination ONLY, NOT direct
distillation/blending-line control, and it has NO excise/tax-classification
authority.** It does not:
- Direct still operation, fermentation, or blending (operator-exclusive)
- Certify excise-tax compliance or bottling authorization (human-exclusive)
- Reclassify a batch's federal/state excise-tax category (human/tax-authority-exclusive)
- Make final spirit-classification decisions (master-distiller-exclusive)

**Hard invariants (always HOLD, no override):**
- Operation outside the closed op-allowlist (`:op-not-allowed`) — includes
  any proposal that would touch distillation/blending-line control or
  excise/tax-classification-authority decisions
- Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
- Distillery/batch record not independently verified/registered before any
  proposal is made against it (`:batch-not-registered`) — applies to every
  proposal op, not only shipment coordination
- No jurisdiction citation (`:no-spec-basis`)
- Evidence checklist incomplete (`:evidence-incomplete`)
- Proof/ABV out of range for spirit type + jurisdiction (`:proof-out-of-range`)
- Measured ABV outside the declared batch's tolerance band
  (`:abv-out-of-tolerance`) — a simple, symmetric target±tolerance check
  (mirrors ISIC 1102's `abv-in-tolerance?`); crossing the band risks an
  excise-tax-class misclassification, a decision this actor never makes
- Age statement insufficient — minimum aging period not met (`:age-statement-insufficient`)
- Tax mark missing (`:tax-mark-missing`)
- Bottle label not approved by jurisdiction authority (`:label-not-approved`)
- Production record incomplete (`:production-record-incomplete`)
- Batch already processed / shipment already finalized (double-commit guards)

**Soft gates (always escalate for human):**
- Low confidence (<60%)
- Real actuation (`:log-production-batch`, `:coordinate-shipment`)
- Food-safety concerns (`:flag-food-safety-concern`) — e.g. methanol-cut
  timing, contamination — never auto-resolved by advisor confidence alone

## Operations

Proposal ops (closed allowlist, all `:effect :propose`):
- `:log-production-batch` — distillation/blending batch, proof/ABV,
  cask/aging data logging (always requires human sign-off)
- `:schedule-maintenance` — still/blending-equipment maintenance scheduling
- `:flag-food-safety-concern` — surface a food-safety concern (e.g.
  methanol-cut timing, contamination); always escalates
- `:coordinate-shipment` — outbound spirits shipment coordination (always
  requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly
anything that would amount to direct distillation/blending-line control, or
an excise/tax-classification-authority decision — is refused unconditionally
by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Jurisdictions

Reference facts for US (TTB), Japan (酒税法), and EU (Regulation 1601/2009)
are baked into `src/distilling/facts.cljc`. Proof standards, age-statement
minimums, and required evidence checklists per jurisdiction.

## Development

### Prerequisites

- Clojure / clj-kondo (for linting)
- cognitect test-runner (in deps.edn)

### Test suite

```bash
clj -M:test
```

All `.cljc` files run on JVM + Node (portable). No JVM-only constructs.

### Demo

```bash
clj -M:dev:run
```

Runs a sample batch-logging workflow through the advisor → governor →
phase gate → commit flow.

### Linting

```bash
clj -M:lint
```

Static analysis via clj-kondo. Errors fail CI.

## AGPL-3.0-or-later

Forkable by any licensed spirits producer, so distilleries never surrender
batch provenance and compliance records to a closed SaaS. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
