# Code Style Rules

## 1. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Class | PascalCase | `ProductVariantService` |
| Interface | PascalCase | `ProductService` (no `I` prefix) |
| Implementation | `{Interface}Impl` | `ProductServiceImpl` |
| Method | camelCase | `getProductVariants` |
| Variable | camelCase | `activeVariantCount` |
| Constant | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE` |
| Package | lowercase | `com.locnguyen.ecommerce.domains.product` |
| DTO request | `{Action}{Domain}Request` | `CreateProductRequest`, `UpdateVariantRequest` |
| DTO response | `{Domain}Response` | `ProductResponse`, `VariantSummaryResponse` |
| Filter DTO | `{Domain}Filter` | `ProductFilter`, `OrderFilter` |
| Mapper | `{Domain}Mapper` | `ProductMapper` |
| Specification | `{Domain}Specification` | `ProductSpecification` |
| Repository | `{Domain}Repository` | `ProductRepository` |
| Controller | `{Domain}Controller` | `ProductController` |

## 2. Service Interface + Implementation

Always create a service interface, never expose the `Impl` class directly:

```java
// Interface
public interface ProductService {
    ProductResponse getProduct(String id);
}

// Implementation
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    ...
}
```

## 3. DTO Separation

- `Request DTOs` — incoming data from client, annotated with Bean Validation
- `Response DTOs` — outgoing data to client, no validation annotations
- Never expose entity fields directly; always map through a response DTO

## 4. Lombok Usage

Use Lombok to reduce boilerplate:
- `@RequiredArgsConstructor` on service/controller for constructor injection
- `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` on entities/DTOs
- `@Data` only on simple value objects, not on JPA entities (causes issues with `equals/hashCode`)
- `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` on JPA entities

## 5. Logging

Use SLF4J with `@Slf4j` (Lombok):

```java
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    log.info("Order created: orderId={}, customerId={}", orderId, customerId);
    log.warn("Inventory low: variantId={}, remaining={}", variantId, remaining);
    log.error("Payment callback failed: provider={}, txnRef={}", provider, txnRef, e);
}
```

**Never log:** passwords, raw JWT, refresh token, OTP, cookie values, payment secrets, full authorization headers.

Log with context: include relevant IDs (orderId, customerId, variantId, txnRef), not just a bare message.

## 6. Exception Handling

Use `AppException` with `ErrorCode`:

```java
throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH, "Variant " + variantId + " has only " + available + " left");
```

Never catch broad `Exception` and swallow it silently:

```java
// Bad
try { ... } catch (Exception e) { log.error("Error"); }

// Good
try { ... } catch (SpecificException e) {
    log.error("Failed to process payment: orderId={}", orderId, e);
    throw new AppException(ErrorCode.PAYMENT_FAILED, e.getMessage());
}
```

## 7. Constants

Use `AppConstants` for shared values. Never hardcode magic strings or numbers in business logic:

```java
// Bad
if (pageable.getPageSize() > 100) ...

// Good
if (pageable.getPageSize() > AppConstants.MAX_PAGE_SIZE) ...
```

Domain-specific constants may live in their own constants class if needed.

## 8. Transaction Boundary

`@Transactional` belongs on service implementation methods only.

- Read-only queries: `@Transactional(readOnly = true)`
- Write operations: `@Transactional` (default propagation REQUIRED)
- Never put `@Transactional` on Controller or Repository unless there is a specific reason

## 9. Avoid These Patterns

- Don't duplicate fetch logic — if a service already provides data, call it
- Don't put conditional logic inside Mapper — mappers are pure transformation
- Don't use raw `Object` or unparameterized collections
- Don't use `Optional.get()` without `isPresent()` check — use `.orElseThrow()`
- Don't use field injection (`@Autowired` on field) — use constructor injection
