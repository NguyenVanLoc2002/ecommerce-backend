# Ecommerce Implementation Roadmap

> Generated: 2026-05-14  
> Based on: source code inspection of `src/main/java`, migrations V1–V24, docs, and `.claude/rules/`

---

## 1. Current Project Snapshot

### Tech Stack
- Java 17 + Spring Boot 3.3.4
- Spring Security (JWT stateless) + Redis (token blacklist, refresh session, cache)
- Spring Data JPA + Hibernate (MariaDB dialect) + Flyway V1–V24
- MapStruct, Lombok, OpenAPI/Springdoc
- MariaDB (primary), Redis (session/OTP/cache), H2 (test)
- **No Elasticsearch, no messaging broker, no external carrier SDK in pom.xml**

### Architecture
Layered modular monolith: `Controller → Service → Repository` per domain.  
`common/` owns cross-cutting concerns (security, response wrapper, exception, auditing, validation).  
`infrastructure/` is scaffolded (`email`, `external`, `messaging`, `storage`) but mostly empty stubs.

### Existing Domains (source code confirmed)
| Domain | Status |
|--------|--------|
| `auth` | Full: login, register, refresh-token cookie, logout, forgot/reset/change password, OTP, CSRF double-submit gate |
| `user` | Full: CRUD, role, soft delete |
| `admin` | Full: admin user management |
| `customer` | Entity + repository only (no controller/service files found) |
| `address` | Full: controller, service, repository, entity, mapper |
| `brand` | Full: CRUD, soft delete, specification |
| `category` | Full: CRUD, parent-child, soft delete |
| `product` | Full: CRUD, attributes, soft delete, MariaDB FULLTEXT search (V17), reindex API |
| `productvariant` | Full: variant CRUD, attribute mapping |
| `inventory` | Full: warehouse, inventory, reservation, stock movement, optimistic lock (V20), scheduler |
| `cart` | Full: cart creation, add/update/remove item |
| `order` | Full: create (from cart + idempotency), cancel, status machine, admin management |
| `payment` | Full: COD + online initiate (with paymentUrl/deeplink/qrCodeUrl), callback, webhook (with full HMAC signature gate + amount validation + partnerCode guard + idempotency), refund (initiate), `PaymentProvider` abstraction, `PaymentProviderRegistry`, `MockPaymentProvider`, `MomoPaymentProvider` (create-payment + IPN/webhook fully implemented), `PaypalPaymentProvider` (Session 1: create-order + approval URL; Session 2: capture endpoint + webhook verification via PayPal Webhooks API v1 + isSuccess/extractOrderCode/extractProviderTxnId fully implemented), V8/V22/V23/V24/V25 migrations |
| `idempotency` | Full: `IdempotencyKey` entity, service with PROCESSING/COMPLETED/FAILED states |
| `promotion` | Full: voucher, promotion rule, usage tracking |
| `shipment` | Full: create, status machine (PENDING→PICKING→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED/FAILED→RETURNED), events, optimistic lock (V19) |
| `invoice` | Domain package exists; needs verification |
| `notification` | Full: in-app notification, admin broadcast |
| `review` | Full: create, moderate (admin) |
| `auditlog` | Full: entity, repository, service |
| `verification` | Full: OTP entity + service (used by auth) |

### Existing Infrastructure (stubs)
- `infrastructure/email/` — `EmailSender` interface + `LoggingEmailSender` (stub; logs to console)
- `infrastructure/external/` — empty package-info
- `infrastructure/messaging/` — empty package-info
- `infrastructure/storage/` — empty package-info
- `infrastructure/cache/` — empty package-info

### Existing Security
- JWT: access token in body, refresh token in HttpOnly cookie (`Path=/api/v1/auth`)
- Refresh session: server-side Redis, hashed token, rotation + family revocation
- CSRF: `CsrfDoubleSubmitFilter` (gated, default off in dev)
- OTP: SHA-256(purpose+target+rawOtp+pepper), rate-limited via Redis
- Role hierarchy: `SUPER_ADMIN > ADMIN > STAFF > CUSTOMER`
- Idempotency: `IdempotencyKey` entity with PROCESSING gate for ORDER_CREATE and PAYMENT_INITIATE

### Existing Migrations
V1–V24 applied. Latest: V24 (`payment_refunds`). Next must be V25.

### Product Search
MariaDB FULLTEXT on `products(name, slug, search_text)` — IN BOOLEAN MODE.  
Reindex endpoint: `POST /api/v1/admin/products/search/reindex`.  
**No Elasticsearch in pom.xml or source.**

---

## 2. Gap Analysis

### Already Implemented (confirmed by source)
- Security foundation: JWT, refresh cookie, OTP, CSRF gate, password reset/change
- Optimistic locking: `@Version` on `orders`, `payments`, `shipments`, `inventories` (V19/V20)
- Idempotency key: full service with hash validation, `ORDER_CREATE` and `PAYMENT_INITIATE` gates
- Payment Transaction Core: `Payment`, `PaymentTransaction`, `PaymentWebhookLog`, `PaymentRefund` entities, services, webhook handler with `PaymentProvider` interface + `PaymentProviderRegistry`
- Order state machine: strict transitions, idempotent creation
- Shipment state machine: PENDING→…→DELIVERED/RETURNED
- Inventory reservation + stock movement + optimistic locking + scheduler (expired reservations)
- COD payment flow: create → complete (admin confirms delivery)
- Online payment flow: initiate → callback/webhook → refund
- MariaDB FULLTEXT product search + reindex API

### Partially Implemented
- **Refund completion**: `initiateRefund()` sets `RefundStatus.PENDING` and records a transaction — but there is no `completeRefund()` or provider-side refund callback handler. The `COMPLETED` status of `PaymentRefund` is never set by the service.
- **Carrier / Shipment provider abstraction**: `Shipment.carrier` is a plain `VARCHAR` (e.g., "GHTK"). There is no `Carrier` entity, no `CarrierProvider` interface, no carrier config management. Admin manually fills in `carrier` + `trackingNumber` strings today.
- **Real email sending**: `LoggingEmailSender` logs to console; no SMTP/SES/SendGrid integration.
- **Customer domain**: entity and repository present, but no controller, service, or mapper — customer profile management appears to be missing.
- **Invoice domain**: package `domains/invoice` exists but was not found in the file tree scan — needs verification.

### Missing (not implemented)
- **MoMo IPN webhook handler** (Session 2) — `MomoPaymentProvider.verifySignature()` is implemented; `PaymentWebhookServiceImpl` must call it before state mutation
- **ZaloPay payment integration** (Phase 4)
- **PayPal currency model** — orders are VND; PayPal requires USD. Test-only VND→USD conversion enabled via `app.payment.paypal.test-conversion-enabled=true`. A real currency conversion service is required before production go-live.
- **Carrier infrastructure** (`Carrier` entity, `CarrierConfig`, `CarrierProvider` interface)
- **Ahamove integration** (Phase 6)
- **GHN integration** (Phase 6, if needed)
- **Elasticsearch** — not in pom.xml; MariaDB FULLTEXT is the current search (Phase 7, future)
- **Real email sender** (SMTP/SendGrid) for OTP and order notifications
- **Customer self-service APIs** — profile CRUD, order history, shipment tracking for customers
- **Rate limiting** (beyond OTP throttling) — no general rate limiter middleware found
- **File upload / storage** — `infrastructure/storage` is empty stub; no MinIO/S3 integration

### Risk Areas
| Area | Risk | Severity |
|------|------|----------|
| Payment callback HMAC | `processCallback()` has `TODO(phase-2)` — signature verification not yet implemented. Spoofed callbacks can mark orders as paid. | **CRITICAL (pre-prod)** |
| MoMo IPN signature not wired | ✅ **RESOLVED** — `PaymentWebhookServiceImpl` calls `provider.verifySignature()` + `provider.extractAmount()` before any mutation. partnerCode, HMAC-SHA256, and amount are all validated. | RESOLVED |
| Refund never completes | `PaymentRefund.status` stays `PENDING` forever; no pathway to `COMPLETED`. | MEDIUM |
| Customer domain missing | No customer profile API, order history, or address management for customers identified. | MEDIUM |
| Sensitive config in dev | `app.jwt.secret` and DB password in `application-dev.properties` (not committed to prod). Dev only, but should use env vars. | LOW (dev) |
| Empty email sender | OTP emails and order confirmations are logged, never sent. | MEDIUM (pre-prod) |

---

## 3. Implementation Principles

- Do not integrate real providers (MoMo, ZaloPay, Ahamove) before core abstractions are stable and tested with mock providers.
- Do not generate live payment URLs before HMAC/signature verification is implemented — or at minimum document the risk clearly.
- Do not hardcode provider-specific logic into `PaymentServiceImpl` or `ShipmentServiceImpl`; always go through `PaymentProvider` / `CarrierProvider` abstraction.
- Do not process payment without idempotency and transaction safety — both are already in place; preserve them when extending.
- Do not create shipment against a carrier provider before `CarrierProvider` abstraction is built.
- Do not log provider API keys, HMAC secrets, raw callback payloads (body logged in `PaymentTransaction.payload` — use caution), or OTP codes.
- Keep API contract and docs in sync with every controller change.
- Never edit existing Flyway migrations V1–V25.
- Next migration must be V26.

---

## 4. Phase 1 — Security Foundation

### Status: **COMPLETE**

All items below are confirmed implemented in source.

**Implemented:**
- [x] JWT access token (Bearer) + refresh token (HttpOnly cookie)
- [x] Refresh session: server-side Redis, hashed token, rotation, family revocation
- [x] CSRF double-submit cookie filter (gated via `app.security.csrf-double-submit-enabled`)
- [x] Forgot password → OTP verify → reset token → reset password flow
- [x] Change password (with current password verification + all session revocation)
- [x] BCrypt(12) password encoder
- [x] Role hierarchy: SUPER_ADMIN > ADMIN > STAFF > CUSTOMER
- [x] `@PreAuthorize` method-level authorization
- [x] Input validation via Bean Validation + custom `@PhoneNumber`
- [x] OTP: SHA-256 hash + pepper, rate-limited (cooldown + rolling window)
- [x] Password policy validator (`PasswordPolicyValidator`)
- [x] Sensitive logging rules documented and applied in existing services

**Remaining (before production):**
- [ ] Remove `app.security.refresh-token-body-fallback-enabled` support entirely once FE migrated off it
- [ ] Enable `app.security.csrf-double-submit-enabled=true` in production
- [ ] Confirm `Secure=true` on refresh cookie in production (`AUTH_REFRESH_COOKIE_SECURE`)
- [ ] Remove DB credentials from `application-dev.properties`; use env vars even in dev

### Suggested Branch
`chore/security_prod_hardening`

---

## 5. Phase 2 — Concurrency, Idempotency, and Order/Inventory Safety

### Status: **COMPLETE**

All items below are confirmed implemented.

**Implemented:**
- [x] Optimistic locking (`@Version`) on `Inventory`, `Order`, `Payment`, `Shipment` (V11, V19, V20)
- [x] `IdempotencyKey` entity + `IdempotencyService` with PROCESSING/COMPLETED/FAILED states
- [x] Idempotency enforced on `POST /api/v1/orders` (ORDER_CREATE) and `POST /api/v1/payments/order/{orderId}/initiate` (PAYMENT_INITIATE)
- [x] Request hash validation (same key + different payload → IDEMPOTENCY_KEY_CONFLICT)
- [x] DB unique constraint on `payment_transactions.provider_txn_id` (V22)
- [x] Row-level lock on Payment before callback processing (`findByOrderIdWithLock`)
- [x] Inventory reservation + deduction in same transaction
- [x] `InventoryScheduler` releases expired reservations

**No new work required in this phase.**

---

## 6. Phase 3 — Payment Transaction Core (Finalization)

### Status: **~80% complete — 3 gaps remain**

**Implemented:**
- [x] `Payment` entity (one per order, optimistic lock, version)
- [x] `PaymentTransaction` (immutable audit trail, provider_txn_id unique constraint)
- [x] `PaymentWebhookLog` (pre-processing audit, committed in own transaction)
- [x] `PaymentRefund` (partial refund support, amount guard)
- [x] `PaymentProvider` interface + `PaymentProviderRegistry`
- [x] COD create + complete flow
- [x] Online payment initiate (with idempotency gate)
- [x] Webhook processing with signature gate (calls `provider.verifySignature()`)
- [x] Duplicate webhook guard (providerTxnId check)
- [x] Race condition guard (row-level lock before state mutation)
- [x] Refund initiation (PENDING state recorded)
- [x] All status enums: `PaymentRecordStatus`, `TransactionStatus`, `RefundStatus`

**Missing:**

### Gap 3.1 — Mock Payment Provider
No concrete `PaymentProvider` bean exists → `PaymentProviderRegistry` always returns `Optional.empty()`.

**Backend Tasks:**
- [x] Create `com.locnguyen.ecommerce.infrastructure.payment.mock.MockPaymentProvider implements PaymentProvider`
  - `getProviderName()` → `"MOCK"`
  - `verifySignature(rawBody, signature)` → returns `true` always (dev/test only)
  - `isSuccess(payload)` → parses `"status": "SUCCESS"` or `"SUCCESS"` string from simple JSON/query-param payload
  - `extractProviderTxnId(payload)` → parses `"providerTxnId"` field
  - `extractOrderCode(payload)` → parses `"orderCode"` field
- [ ] Gate with `@ConditionalOnProperty(name = "app.payment.mock.enabled", havingValue = "true")` so it is never active in prod
- [ ] Add `app.payment.mock.enabled=true` in `application-dev.properties`

**Test Cases:**
- [ ] `MockPaymentProviderTest`: verify all extraction methods with valid/malformed payload
- [ ] `PaymentWebhookServiceImplTest`: assert MOCK webhook succeeds end-to-end (success + failure callbacks)
- [ ] Assert registry resolves MOCK provider correctly

**Risks:** None in dev. Must be disabled in prod via property gate.

---

### Gap 3.2 — Payment URL in `initiateOnlinePayment()`
`PaymentResponse` currently has no `paymentUrl` field. Customer calls initiate but gets no redirect URL.

**Backend Tasks:**
- [ ] Add `paymentUrl` field to `PaymentResponse` DTO
- [ ] Extend `PaymentProvider` interface with:
  ```java
  String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl);
  ```
- [ ] Add `createPaymentUrl(...)` call in `PaymentServiceImpl.executeInitiateOnlinePayment()`
- [ ] `MockPaymentProvider.createPaymentUrl(...)` → returns `"http://localhost:8080/api/v1/payments/mock/complete?orderCode={code}&status=SUCCESS"`
- [ ] Add mock completion endpoint: `GET /api/v1/payments/mock/complete?orderCode=&status=` (dev-only, `@ConditionalOnProperty`)
- [ ] Add `returnUrl` and `callbackUrl` config properties for each environment
- [ ] Add `app.payment.return-url`, `app.payment.callback-url` to `AppProperties`

**API Contract Impact:**
- `POST /api/v1/payments/order/{orderId}/initiate` response: add `paymentUrl` field
- Update `docs/customer-api-contract.md`

**Test Cases:**
- [ ] `PaymentServiceTest`: assert `paymentUrl` non-null when provider is MOCK
- [ ] `MockPaymentProvider`: assert URL contains expected order code

---

### Gap 3.3 — Refund Completion
`initiateRefund()` sets `RefundStatus.PENDING` but nothing ever completes it.

**Backend Tasks:**
- [ ] Add `completeRefund(String refundCode, String providerRefundId)` to `PaymentRefundService`
- [ ] Implement in `PaymentRefundServiceImpl`: set `COMPLETED`, set `refundedAt`, set `providerRefundId`
- [ ] Add admin endpoint: `POST /api/v1/admin/payments/refunds/{refundCode}/complete`
- [ ] For real providers: `PaymentProvider` will need a `processRefund(...)` method (Phase 4)
- [ ] Mock: `completeRefund` is manual admin action (no provider call needed)

**Test Cases:**
- [ ] `PaymentRefundServiceTest`: assert complete transitions PENDING → COMPLETED
- [ ] Assert over-complete guard (idempotent if already COMPLETED)

### Suggested Branch
`feat/payment_mock_provider_and_url`

---

## 7. Phase 4 — Payment Provider Integrations

### Status: **NOT STARTED — do not start before Phase 3 gaps are resolved**

Prerequisite: Phase 3 complete (mock provider tested end-to-end, `paymentUrl` returned, refund completion implemented).

### 7.1 Shared Provider Contract

Before implementing any real provider, extend `PaymentProvider`:

```java
public interface PaymentProvider {
    String getProviderName();
    String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl);
    boolean verifySignature(String rawBody, String signature);
    boolean isSuccess(String payload);
    String extractProviderTxnId(String payload);
    String extractOrderCode(String payload);
    // Phase 4 addition:
    RefundResult processRefund(PaymentRefund refund, Payment payment);
    PaymentStatusResult queryPaymentStatus(String providerTxnId);
}
```

Add `RefundResult` and `PaymentStatusResult` value objects in `payment/provider/`.

### 7.2 Provider Config per Environment

**Backend Tasks:**
- [ ] Create `PaymentProviderConfig` record/class in `common/config/` or `payment/config/`
  - Fields: `enabled`, `apiKey` (encrypted at rest), `secretKey` (encrypted at rest), `merchantId`, `baseUrl`, `returnUrl`, `callbackUrl`
- [ ] Add `@ConfigurationProperties(prefix = "app.payment.providers.momo")` etc.
- [ ] Store encrypted secrets in env vars — never in source

### 7.3 MoMo Integration

**Backend Tasks:**
- [ ] `MomoPaymentProvider implements PaymentProvider` in `payment/provider/`
- [ ] Create payment request: `POST https://payment.momo.vn/v2/gateway/api/create`
- [ ] HMAC-SHA256 signature generation
- [ ] `createPaymentUrl()` → returns MoMo redirect URL
- [ ] `verifySignature()` → validates HMAC from incoming webhook
- [ ] `isSuccess()` → checks `resultCode == 0`
- [ ] `extractProviderTxnId()` → `momoTransId`
- [ ] `extractOrderCode()` → `orderId` (mapped to our `orderCode`)
- [ ] `processRefund()` → call MoMo refund API
- [ ] Sandbox config via `app.payment.providers.momo.*`
- [ ] Log request/response bodies (redact secret key + signature in logs)

**Test Cases:**
- [ ] `MomoPaymentProviderTest`: unit test signature generation with known vectors
- [ ] `MomoPaymentProviderTest`: verify `isSuccess()` handles all MoMo result codes

### 7.4 ZaloPay Integration

**Backend Tasks:**
- [ ] `ZaloPayPaymentProvider implements PaymentProvider`
- [ ] ZaloPay v2 create order API (`apptransid`, `apptime`, HMAC-SHA256 with `key1`)
- [ ] Webhook signature verification (`key2`)
- [ ] `processRefund()` → ZaloPay refund API
- [ ] Sandbox config via `app.payment.providers.zalopay.*`

### 7.5 PayPal Integration

**Note:** PayPal is more relevant for international payments. Confirm business requirement before investing.  
If confirmed:
- [ ] PayPal REST SDK or direct API calls
- [ ] `PayPalPaymentProvider implements PaymentProvider`
- [ ] OAuth 2.0 client credentials for API auth
- [ ] IPN/webhook signature via PayPal cert verification

### 7.6 Risks
- Each provider has different sandbox behavior — test with real sandbox accounts
- HMAC verification must be active before going live (currently `TODO(phase-2)` in `PaymentServiceImpl.processCallback()`)
- Provider API keys must never appear in logs or Git

### Suggested Branch
`feat/payment_momo_integration` (one branch per provider)

---

## 8. Phase 5 — Carrier Infrastructure

### Status: **NOT STARTED**

Current state: `Shipment.carrier` is a plain `VARCHAR`. Admin fills it manually. No carrier entity, no provider abstraction.

### 8.1 Carrier Entity + Config

**Database/Migration Tasks (V25):**
```sql
-- V25__create_carrier_tables.sql
CREATE TABLE carriers (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    provider_type VARCHAR(100) NOT NULL,  -- e.g. AHAMOVE, GHN, GHTK, MANUAL
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    logo_url     VARCHAR(500) NULL,
    description  VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(255) NULL,
    updated_by   VARCHAR(255) NULL,
    CONSTRAINT uq_carriers_code UNIQUE (code)
);

CREATE TABLE store_carrier_configs (
    id             CHAR(36)      NOT NULL PRIMARY KEY,
    carrier_id     CHAR(36)      NOT NULL,
    api_key_enc    VARCHAR(1000) NULL,   -- AES-encrypted at application layer
    secret_key_enc VARCHAR(1000) NULL,
    webhook_secret_enc VARCHAR(1000) NULL,
    base_url       VARCHAR(500)  NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    config_json    TEXT          NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(255)  NULL,
    updated_by     VARCHAR(255)  NULL,
    CONSTRAINT fk_scc_carrier FOREIGN KEY (carrier_id) REFERENCES carriers(id)
);

CREATE INDEX idx_scc_carrier_id ON store_carrier_configs(carrier_id);
```

**Backend Tasks:**
- [ ] `Carrier` entity + `CarrierConfig` entity in `domains/shipment/entity/` (or new `domains/carrier/`)
- [ ] `CarrierProvider` interface in `domains/shipment/provider/`:
  ```java
  public interface CarrierProvider {
      String getProviderType();
      ShippingRateResult calculateRate(ShippingRateRequest request);
      ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config);
      boolean cancelShipment(String carrierShipmentId, CarrierConfig config);
      ShipmentTrackingResult getTracking(String trackingNumber, CarrierConfig config);
      ShipmentStatus mapStatus(String carrierStatus);
  }
  ```
- [ ] `CarrierProviderRegistry` (same pattern as `PaymentProviderRegistry`)
- [ ] `MockCarrierProvider implements CarrierProvider` (gated, dev-only)
- [ ] `CarrierService` interface + `CarrierServiceImpl`
- [ ] `CarrierAdminController`: CRUD for carriers, enable/disable, save config (encrypted keys)
- [ ] AES encryption helper for `api_key_enc` / `secret_key_enc` columns
- [ ] Encrypt sensitive config before saving, decrypt before use — never log raw keys

**API Contract Tasks:**
- [ ] `GET /api/v1/admin/carriers` — list carriers
- [ ] `POST /api/v1/admin/carriers` — create carrier
- [ ] `PUT /api/v1/admin/carriers/{id}/config` — save encrypted config
- [ ] `PATCH /api/v1/admin/carriers/{id}/toggle` — enable/disable
- [ ] Add carrier-related error codes to `ErrorCode`: `CARRIER_NOT_FOUND`, `CARRIER_CONFIG_MISSING`, `CARRIER_REQUEST_FAILED`
- [ ] Update `docs/admin-api-contract.md`

**Test Cases:**
- [ ] `CarrierServiceTest`: assert enable/disable transitions
- [ ] `MockCarrierProvider`: assert `createShipment()` returns mock tracking number
- [ ] `CarrierAdminControllerTest`: CRUD endpoints, config encryption check (key not in response)

### 8.2 Link Shipment to Carrier

When carrier abstraction is ready:
- [ ] Add nullable `carrier_id FK → carriers(id)` column to `shipments` table (V26 migration — backward compatible, nullable)
- [ ] `ShipmentServiceImpl.createShipment()`: when `carrierId` is provided, resolve `CarrierProvider`, call `createShipment()`, store `trackingNumber` returned by provider
- [ ] Maintain backward compat: if `carrierId` is null, fall back to manual `carrier` + `trackingNumber` string (existing behavior preserved)

### Risks
- AES key management: encryption key must come from env var, not hardcoded
- Do not migrate existing `carrier` string data into FK references in the same migration — do it separately after data cleanup

### Suggested Branch
`feat/carrier_infrastructure`

---

## 9. Phase 6 — Carrier Provider Integrations

### Status: **NOT STARTED — do not start before Phase 5 is complete and tested**

Prerequisite: `CarrierProvider` abstraction, `MockCarrierProvider` tested, `store_carrier_configs` table with encryption.

### 9.1 Ahamove Integration

**Backend Tasks:**
- [ ] `AhamoveCarrierProvider implements CarrierProvider` in `domains/shipment/provider/` or `infrastructure/external/ahamove/`
- [ ] Auth: Ahamove uses API key in header (`api-key`)
- [ ] `calculateRate()` → `POST https://apistaging.ahamove.com/v1/order/estimated_fee` (sandbox)
- [ ] `createShipment()` → `POST /v1/order/create`
- [ ] `cancelShipment()` → `POST /v1/order/cancel`
- [ ] `getTracking()` → `GET /v1/order/info`
- [ ] `mapStatus()`: map Ahamove statuses (`IDLE`, `ASSIGNING`, `ACCEPTED`, `IN_PROCESS`, `COMPLETED`, `CANCELLED`, `FAILED`) to internal `ShipmentStatus`
- [ ] Webhook: `POST /api/v1/shipments/webhook/ahamove` (public endpoint, add to `SecurityConfig`)
  - Verify Ahamove webhook signature (token-based)
  - Map status update → `ShipmentService.updateStatus()`
  - Record `ShipmentEvent`
- [ ] Config via `store_carrier_configs` table (API key encrypted)
- [ ] Log request/response (redact API key header)
- [ ] Retry on transient errors (HTTP 5xx, timeout) — use Spring `@Retryable` or manual retry

**API Contract Tasks:**
- [ ] Add `POST /api/v1/shipments/webhook/ahamove` to `SecurityConfig.PUBLIC_POST`
- [ ] Update `docs/admin-api-contract.md` with Ahamove carrier config management

**Test Cases:**
- [ ] `AhamoveCarrierProviderTest`: status mapping with all Ahamove status codes
- [ ] `AhamoveCarrierProviderTest`: signature verification with known test vectors
- [ ] Integration: create shipment via MOCK → assert event recorded; repeat with Ahamove sandbox

### 9.2 GHN Integration (if needed)

Assess after Ahamove is stable. GHN has well-documented API but adds maintenance burden.  
Decision criteria: does the business use GHN or can Ahamove cover all routes?

**If confirmed:**
- [ ] `GhnCarrierProvider implements CarrierProvider`
- [ ] GHN V2 API: create order, cancel order, tracking, webhook
- [ ] Map GHN status codes to internal `ShipmentStatus`

### 9.3 Shipment Webhook Security

All carrier webhooks are public endpoints. Each must verify the carrier-specific signature/token before any DB mutation.

- [ ] Add `CARRIER_WEBHOOK_SIGNATURE_INVALID` to `ErrorCode`
- [ ] `CarrierProvider.verifyWebhookSignature(String rawBody, String signature, CarrierConfig config)`
- [ ] Webhook log table for carrier webhooks (similar to `payment_webhook_logs`) — V27 migration

### Suggested Branch
`feat/carrier_ahamove_integration`

---

## 10. Phase 7 — Elasticsearch Product Search

### Status: **NOT STARTED — lower priority; MariaDB FULLTEXT is functional for MVP**

**Current state:** MariaDB FULLTEXT search on `products(name, slug, search_text)` is implemented and working.  
Reindex endpoint exists at `POST /api/v1/admin/products/search/reindex`.  
Elasticsearch is **not in pom.xml**.

**Decision checkpoint:** Only proceed if MariaDB FULLTEXT is insufficient at scale (rough threshold: >500K products or complex multi-facet filters with heavy load). Measure first.

### 10.1 Infrastructure Setup

**Tasks (do not start before decision):**
- [ ] Add `spring-boot-starter-data-elasticsearch` to `pom.xml`
- [ ] Add Elasticsearch Docker service to `docker-compose.yml`
- [ ] Configure `spring.elasticsearch.uris`, `spring.elasticsearch.username/password` in `application-dev.properties`
- [ ] Add `elasticsearch.index.products` config property

### 10.2 Product Search Document

**Backend Tasks:**
- [ ] Create `ProductSearchDocument` in `domains/product/search/`
  ```java
  @Document(indexName = "products")
  record ProductSearchDocument(
      String id, String name, String slug, String description,
      String brandName, List<String> categoryNames,
      BigDecimal minPrice, BigDecimal maxPrice,
      List<String> attributeValues, String status,
      boolean isDeleted, Instant createdAt
  )
  ```
- [ ] `ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, String>`
- [ ] `ProductSearchService` interface + `ElasticsearchProductSearchServiceImpl`
- [ ] Fallback: if Elasticsearch is down → fall back to existing MariaDB FULLTEXT query (circuit breaker or try-catch)

### 10.3 Sync Strategy

- [ ] Sync on product create/update/delete (call `ProductSearchService.index()` after DB save)
- [ ] Sync on variant price change (rebuild `minPrice`/`maxPrice`)
- [ ] Sync on soft delete (mark `isDeleted = true` in index or delete from index)
- [ ] Admin reindex: extend existing `POST /api/v1/admin/products/search/reindex` to support both MariaDB and ES

### 10.4 Search API

Option A: Introduce new endpoint `GET /api/v1/products/search` backed by ES, keep existing `GET /api/v1/products` backed by MariaDB.  
Option B: Route existing `GET /api/v1/products` to ES when available — requires strategy pattern.

**Recommended: Option A** — clean separation, no risk to existing API contract.

- [ ] `GET /api/v1/products/search?q=&brand=&category=&minPrice=&maxPrice=&page=&size=`
- [ ] `GET /api/v1/products/suggest?q=` — autocomplete, returns top 5-10 suggestions
- [ ] Pagination: standard `PagedResponse<ProductSearchResult>`
- [ ] Filter: brand, category (multi-value), price range, in-stock only

### 10.5 Test Cases
- [ ] `ProductSearchServiceTest`: index, update, delete, fallback behavior
- [ ] `ProductSearchControllerTest`: search and suggest endpoints with filter combinations
- [ ] Fallback test: assert MariaDB path taken when ES connection is unavailable

### Suggested Branch
`feat/elasticsearch_product_search`

---

## 11. Phase 8 — Production Hardening (Cross-cutting, defer to pre-launch)

These items should be resolved before the first production deployment, regardless of phase:

- [ ] Implement real email sender (SMTP/SendGrid/SES) in `infrastructure/email/`
  - OTP emails currently only logged to console
  - Order confirmation, shipment update emails not sent
  - `EmailSender` interface is ready — implement `SmtpEmailSender` or `SendGridEmailSender`
- [ ] Implement `PAYMENT_HMAC_SIGNATURE` verification per provider — the `TODO(phase-2)` in `PaymentServiceImpl.processCallback()` **must** be resolved before any real provider goes live
- [ ] Customer self-service APIs — `customer` domain has entity/repository but no controller or service; customers need profile management, order history, saved addresses
- [ ] Rate limiting middleware (beyond OTP throttling) — general API rate limiter (Spring's `Bucket4j` or gateway-level)
- [ ] MinIO/S3 storage for product media uploads — `infrastructure/storage` is empty stub
- [ ] Remove `application-dev.properties` DB password from source; use `.env` or env vars
- [ ] Enable `app.security.csrf-double-submit-enabled=true` and `Secure=true` cookie in production
- [ ] Disable Swagger in production (`springdoc.swagger-ui.enabled=false`)
- [ ] Add integration tests for full order-to-payment flows

---

## 12. Recommended Execution Order

```
1.  Phase 3 Gap 3.1 — MockPaymentProvider (unblocks webhook E2E testing)
2.  Phase 3 Gap 3.2 — paymentUrl in initiateOnlinePayment (unblocks online checkout E2E)
3.  Phase 3 Gap 3.3 — Refund completion endpoint (closes payment lifecycle)
4.  Phase 8 — Customer self-service APIs (blocks FE and mobile)
5.  Phase 8 — Real email sender (blocks OTP and order notifications)
6.  Phase 5 — Carrier Infrastructure (Carrier entity, CarrierProvider, MockCarrier)
7.  Phase 6 — Ahamove Integration (after Phase 5 stable)
8.  Phase 4 — MoMo Integration (after Phase 3 fully stable)
9.  Phase 4 — ZaloPay Integration
10. Phase 4 — PayPal Integration (only if business confirmed)
11. Phase 8 — Storage (MinIO/S3 for product media)
12. Phase 7 — Elasticsearch (only after load/scale assessment)
```

---

## 13. Claude/Codex Task Splitting Guide

### Claude Backend Tasks (per session — keep each session focused)

**Session A (Phase 3.1):** Implement `MockPaymentProvider` + unit tests  
**Session B (Phase 3.2):** Add `paymentUrl` to `PaymentResponse` + extend `PaymentProvider` interface + mock URL generation + mock completion endpoint  
**Session C (Phase 3.3):** Implement `completeRefund()` + admin endpoint + tests  
**Session D (Phase 8 — Customer):** Customer service + controller (profile, order history, address management)  
**Session E (Phase 8 — Email):** `SmtpEmailSender` or `SendGridEmailSender` implements `EmailSender`  
**Session F (Phase 5):** V25 migration + `Carrier` entity + `CarrierProvider` + `MockCarrierProvider` + admin controller  
**Session G (Phase 5.2):** Link `Shipment` to `Carrier` (V26 migration + service update)  
**Session H (Phase 6):** `AhamoveCarrierProvider` + webhook + tests  
**Session I (Phase 4):** `MomoPaymentProvider` + tests  
**Session J (Phase 4):** `ZaloPayPaymentProvider` + tests  

### Frontend / Admin Tasks (dependent on backend sessions)

- Admin: Carrier management UI — depends on Session F
- Admin: Mock payment test UI — depends on Session B
- Customer: Profile + order history — depends on Session D
- Customer: Payment redirect flow — depends on Session B
- Customer: Shipment tracking — depends on Session G+H

### QA Tasks

- After Session A+B+C: E2E payment flow (initiate → mock redirect → webhook → confirm paid → refund)
- After Session F+G: E2E shipment flow (create order → PROCESSING → create shipment → carrier events → DELIVERED)
- After Session I+J: sandbox payment with MoMo / ZaloPay test accounts

---

## 14. Verification Checklist (per session)

- [ ] `.\mvnw.cmd -DskipTests compile` — build passes
- [ ] `.\mvnw.cmd verify` — all tests pass
- [ ] `.\mvnw.cmd spotless:check` — code style clean
- [ ] No new `TODO` / `FIXME` left uncommitted without tracking issue
- [ ] API contract docs updated if endpoint shape changed
- [ ] No sensitive data in logs (API keys, HMAC secrets, OTP, raw tokens)
- [ ] No provider logic hardcoded into core service (always via provider abstraction)
- [ ] No new Flyway migration conflicts with existing V1–V24
- [ ] Next migration number verified before commit (check highest V in `db/migration/`)
- [ ] `git diff` reviewed — no `.env`, no credentials, no `application-prod.properties`

---

## 15. Next Immediate Task

**Recommended first implementation task: Phase 3 Gap 3.1 — MockPaymentProvider**

**Why:**  
The `PaymentProviderRegistry` currently has zero registered beans. Every call to the webhook endpoint (`PaymentWebhookServiceImpl`) resolves the provider as empty and logs a warning — the entire webhook processing path is dead. Implementing `MockPaymentProvider` with a dev-only gate:
1. Makes the webhook handler functional for the first time
2. Enables E2E testing of the full payment lifecycle without real credentials
3. Unblocks Gap 3.2 (payment URL) since the mock provider will also generate the redirect URL
4. All webhook infrastructure (signature gate, duplicate guard, row-level lock) was built correctly — it just needs one provider bean to exercise it

**Scope:** One new file (`MockPaymentProvider.java`) + one property in `application-dev.properties` + tests.

**Prompt to run next:**
```
/implement-feature

Phase 3 Gap 3.1: Implement MockPaymentProvider

Context:
- PaymentProvider interface: src/main/java/com/locnguyen/ecommerce/domains/payment/provider/PaymentProvider.java
- PaymentProviderRegistry: src/main/java/com/locnguyen/ecommerce/domains/payment/provider/PaymentProviderRegistry.java
- MomoPaymentProvider: src/main/java/com/locnguyen/ecommerce/infrastructure/payment/momo/MomoPaymentProvider.java
- MockPaymentProvider: src/main/java/com/locnguyen/ecommerce/infrastructure/payment/mock/MockPaymentProvider.java
- PaymentWebhookServiceImpl: src/main/java/com/locnguyen/ecommerce/domains/payment/service/impl/PaymentWebhookServiceImpl.java

Task:
1. Create MockPaymentProvider in the same package as PaymentProvider.
   - getProviderName() → "MOCK"
   - verifySignature() → always true (dev/test only)
   - isSuccess() → checks JSON field "status" == "SUCCESS" (case-insensitive)
   - extractProviderTxnId() → parses "providerTxnId" JSON field
   - extractOrderCode() → parses "orderCode" JSON field
2. Gate with @ConditionalOnProperty(name = "app.payment.mock.enabled", havingValue = "true")
3. Add app.payment.mock.enabled=true to application-dev.properties
4. Write MockPaymentProviderTest covering all methods with valid + malformed payloads
5. Write PaymentWebhookServiceImplTest asserting a MOCK SUCCESS webhook:
   - creates a PaymentTransaction with status SUCCESS
   - updates Payment to PAID
   - updates Order.paymentStatus to PAID
   - marks webhook log as PROCESSED
6. Follow existing naming and style conventions from the codebase.
```
