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

**This actor supports plant-operations coordination ONLY, NOT process
control.** It does not:
- Direct still operation, fermentation, or blending (operator-exclusive)
- Certify excise-tax compliance or bottling authorization (human-exclusive)
- Make final spirit-classification decisions (master-distiller-exclusive)

**Hard invariants (always HOLD, no override):**
- Batch must exist and be verified before any operation
- Proof/ABV must fall within legal bounds per spirit type + jurisdiction
- Age statements must be verified against minimum aging period (if required)
- Tax mark must be applied before logging into production
- Bottle label must be approved by jurisdiction authority
- Production records must be complete per jurisdiction

**Soft gates (always escalate for human):**
- Low confidence (<60%)
- Real actuation (`:log-production-batch`, `:coordinate-shipment`)
- Compliance concerns (`:flag-compliance-concern`)

## Operations

Proposal ops (closed allowlist, all `:effect :propose`):
- `:log-production-batch` — routine batch logging into production records
- `:schedule-maintenance` — equipment maintenance scheduling
- `:flag-compliance-concern` — surface excise-tax/labeling/proof concerns
- `:coordinate-shipment` — outbound product shipment coordination

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
