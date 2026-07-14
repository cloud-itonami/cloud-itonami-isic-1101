# Security

## Reporting security vulnerabilities

If you discover a security vulnerability, do NOT open a public issue.
Instead, email security@cloud-itonami.cloud with:

- Description of the vulnerability
- Steps to reproduce
- Impact assessment
- Proposed fix (if you have one)

We aim to respond within 48 hours and coordinate a fix before public disclosure.

## Regulatory compliance

This actor enforces spirits-production regulatory compliance (proof/ABV,
age statement, excise mark, labeling) per jurisdiction (US TTB, Japan 酒税法,
EU 1601/2009).

The Governor is the independent compliance layer. It is separated from the
LLM advisor to ensure that regulatory constraints cannot be bypassed by
adversarial prompts or LLM-side logic errors.

## Audit logging

All operations produce immutable audit facts (`:advisor-proposed`, `:governor-hold`,
`:approval-requested`, `:committed`). These facts are the SSoT for compliance
verification and regulatory inspection. Do not mutate or delete audit facts.

## Supply chain

This repository depends only on:
- Clojure standard library (zero transitive dependencies in `deps.edn`).
- `cognitect-labs/test-runner` (for testing only).
- `clj-kondo` (for linting only, no runtime dependency).

Future: `langgraph-clj` and `langchain-clj` (for production advisory).

## Cryptography

This actor does not implement cryptography. Proof/ABV validation is arithmetic
only. For signed batch records and PKI, see kotoba-server or other integration
points.
