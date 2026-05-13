# Testing Rules

## 1. What to Test

### Always test when implementing or changing:

| Changed area | Test type |
|---|---|
| Service business logic | Unit test — mock repository, test all paths |
| Business validation (inventory, voucher, state transition) | Unit test — cover both valid and invalid inputs |
| Repository queries / Specifications | Integration test — use real DB or TestContainers |
| Controller endpoints | `@WebMvcTest` or integration test |
| Auth flow (login, refresh, logout) | Integration test |
| Payment callback (idempotency) | Integration test |
| Promotion/voucher calculation | Unit test — edge cases: min order, usage limit, expiry |
| Order status transitions | Unit test — state machine paths |
| Bug fix | Regression test — reproduce first, then fix |

## 2. Test Priority

1. **Service business rules** — validate all acceptance criteria
2. **Error paths** — not just the happy path; test 404, 409, 422, 403, 401
3. **Boundary conditions** — zero quantity, max voucher usage, expired OTP, concurrent inventory update
4. **Integration tests** — auth flow, order creation, voucher application, payment callback idempotency

## 3. Test Paths Required for Every Feature

For every service method:
- Success path (valid inputs, expected output)
- Validation error (invalid/missing inputs)
- Not found (entity does not exist)
- Unauthorized / Forbidden (wrong role or ownership)
- Conflict / business rule violation (duplicate, invalid state, insufficient stock)

For list endpoints additionally:
- Pagination (non-zero page, custom size)
- Filter behavior (each filter param in isolation and combined)
- Sort direction

## 4. Test Naming Convention

```java
// Pattern: methodName_condition_expectedBehavior
@Test
void createOrder_whenInventoryNotEnough_throwsInventoryNotEnoughException()

@Test
void applyVoucher_whenVoucherExpired_throwsVoucherExpiredException()

@Test
void getProducts_whenKeywordProvided_returnsFilteredResults()
```

## 5. Mocking Rules

- Mock repositories and external dependencies in unit tests
- Use `@MockitoBean` (Spring Boot 3.4+) or `@MockBean` for Spring integration tests
- Do not mock the service under test
- Do not mock MapStruct mappers — use real mapper in unit tests for correctness

## 6. Run Commands

```bash
# Run all tests
.\mvnw.cmd test

# Run specific test class
.\mvnw.cmd -Dtest=ProductServiceTest test

# Run multiple test classes
.\mvnw.cmd -Dtest="OrderServiceTest,InventoryServiceTest" test

# Verify (compile + test + package)
.\mvnw.cmd verify
```
