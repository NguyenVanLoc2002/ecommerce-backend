# Code Review Template

Review the current change set with focus on:

- Correctness and behavioral regressions
- Security and authorization gaps
- Input validation
- Transaction boundaries
- Query performance and N+1 risk
- Concurrency and locking behavior
- Idempotency handling
- Clean code and maintainability
- Test coverage and missing regression tests

## Output

- Critical findings with `file:line`
- Warnings with `file:line`
- API/docs impact
- Missing tests
- Brief overall assessment
