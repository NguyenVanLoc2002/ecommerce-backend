# Refactor Template

## Refactor scope

`<describe the refactor scope>`

## Hard rule

- No behavior change.
- No API contract change.
- No schema or migration change.

## Before/after explanation

- What is being simplified, clarified, or reorganized?
- Why is the new shape safer or easier to maintain?

## Tests

- Keep existing tests passing.
- Add coverage only if needed to prove behavior stayed the same.

## Risk assessment

- Note any areas where behavior could accidentally drift.
- If the refactor feels risky, stop and propose a narrower change.
