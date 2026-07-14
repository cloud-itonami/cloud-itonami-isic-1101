# Governance

This project is part of the [cloud-itonami](https://itonami.cloud) open
business fleet and is governed by the cloud-itonami decision process.

## Decision-making

Decisions are made via the Governor subsystem internal to the actor, not by
a project council. The Governor is the single source of truth for regulatory
compliance and operational constraints. If you think a rule is wrong, open an
issue describing the regulatory basis (cite the jurisdiction law, TTB guidance,
etc.) and propose a change to `distilling.governor` or `distilling.facts`.

## Maintainers

This repository is currently maintained by the cloud-itonami core team.
Contributions are welcome via pull request. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Reporting issues

- **Bug reports**: Open an issue with reproducible steps.
- **Compliance concerns**: Cite the jurisdiction law and describe the
  regulatory gap. These are high-priority.
- **Feature requests**: Describe the use case and regulatory justification
  (if applicable).

## Release cycle

This is not a library with versioned releases. It is a living blueprint that
reflects current regulatory requirements. Changes are merged to `main` and
deployed to operators immediately.

## License

AGPL-3.0-or-later. All contributions must be compatible with this license.
