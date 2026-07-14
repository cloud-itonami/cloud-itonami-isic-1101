# Contributing

This is an AGPL-3.0-or-later open project. Contributions welcome.

## Code of conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Process

1. Fork the repo.
2. Create a branch for your feature/fix.
3. Ensure tests pass: `clj -M:test`
4. Ensure linting passes: `clj -M:lint`
5. Open a PR with a clear description.
6. Wait for review.

## Style

- All source is `.cljc` (portable, no JVM-only constructs).
- Namespace structure mirrors `meatprocessing.governor` pattern.
- Comments in English; Japanese in docstrings for regulatory context is OK.
- Pure functions preferred; mutable state only in Store protocol.

## Testing

- Add tests for new functions in `test/distilling/<module>_test.cljc`.
- Aim for >80% coverage.
- Use `clojure.test` assertions.

## License

All contributions are AGPL-3.0-or-later.
