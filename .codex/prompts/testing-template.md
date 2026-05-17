# Testing Template

## Test scope

`<describe the feature, bugfix, or module under test>`

## Guidance

- Unit tests for service/business rules
- Controller or `@WebMvcTest` coverage for endpoint behavior when relevant
- Integration tests for auth, persistence, idempotency, concurrency, or complex queries

## Edge cases

- Validation failure
- Not found
- Unauthorized/forbidden
- Conflict/business-rule violation
- Empty/result boundary cases

## Regression cases

- Reproduce prior bugs or previously broken paths when applicable

## Command to run tests

```powershell
.\mvnw.cmd -Dtest=<TestClass> test
.\mvnw.cmd test
```
