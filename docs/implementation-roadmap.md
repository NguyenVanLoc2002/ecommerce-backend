# Ecommerce Implementation Roadmap

> Reconciled: 2026-11-04
> Source of truth: current controllers, services, migrations, and tests. API
> endpoint detail remains in `docs/admin-api-contract.md`,
> `docs/customer-api-contract.md`, and `docs/payment-integration.md`.

---

## 1. Current delivery snapshot

The backend is a Java 17 / Spring Boot 3.3.4 modular monolith with MariaDB,
Flyway, Redis, MapStruct, and Springdoc. Schema delivery currently reaches
`V33__enforce_voucher_usage_order_uniqueness.sql`.

The application has delivered the core auth, catalog, inventory, cart, order,
promotion, payment, carrier, shipment, invoice, notification, and review
flows. This roadmap records the current implementation state; it must not be
used to infer a public API that is not documented in the API contracts.

### Payment delivery

- `Payment`, `PaymentTransaction`, `PaymentWebhookLog`, `PaymentRefund`, and
  persisted payment-provider configuration are available.
- Online payment initiation requires an idempotency key and returns
  `paymentUrl`, with optional `deeplink` and `qrCodeUrl` where a provider
  supports them. Customer-owned payment lookup and PayPal capture are present.
- The `PaymentProvider` registry supports the development/test Mock provider,
  MoMo payment creation and signed IPN processing, and PayPal Orders creation,
  capture, and webhook verification.
- Provider webhooks are received at `/api/v1/webhooks/payment/{provider}`
  (with a dedicated PayPal route), verify provider signatures before mutation,
  validate amounts when supplied, log receipt, and ignore duplicate provider
  transaction IDs.
- Refund initiation and admin refund completion are implemented. Completion is
  idempotent for already completed refunds.

### Carrier and AhaMove delivery

- Carrier and carrier-config persistence, provider registry, and the manual,
  mock, and AhaMove provider paths are delivered in migrations V26--V29.
- Customers can list checkout-capable carriers, select a carrier during order
  preview/creation, and receive a server-side shipping quote and carrier
  snapshot on the order.
- Admin APIs provide carrier management plus typed AhaMove integration update,
  connection test, webhook setup, and webhook-token rotation. Raw `configJson`
  remains a legacy compatibility field, not the normal integration path.
- AhaMove supports rate estimates, shipment create/cancel/tracking, and the
  public shipment webhook endpoint. The webhook validates the configured shared
  token, records sanitized events, and handles duplicate events idempotently.

### Current contract status

`docs/customer-api-contract.md` documents checkout carrier selection, payment
initiation/capture, provider webhooks, and AhaMove shipment webhooks.
`docs/admin-api-contract.md` documents carrier configuration and the typed
AhaMove administrative operations. No behavior changed as part of this
documentation reconciliation, so those contracts require no edit in this
Issue.

---

## 2. Remaining work and linked backlog

| Area | Current limitation | Follow-up |
| --- | --- | --- |
| PayPal currency | Orders are priced in VND; the existing VND-to-USD path is test-only and is not a production exchange-rate strategy. | [#33](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/33) |
| AhaMove pickup data | A dedicated pickup/store location is not modelled; carrier configuration still supplies pickup data. | [#34](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/34) |
| Legacy carrier configuration | `configJson` is retained only for compatibility until typed settings can fully replace it. | [#35](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/35), after #34 |
| Product search | MariaDB FULLTEXT is the active search implementation; Elasticsearch is deferred until MVP performance is measured. | [#27](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/27) |

The following are intentionally not represented as delivered carrier/payment
features: a production currency-conversion service, a dedicated pickup-location
domain, automated provider-side refund settlement, additional payment
providers, and removal of legacy carrier configuration.

---

## 3. Evidence and regression coverage

Payment coverage includes `PaymentServiceTest`, `PaymentWebhookServiceImplTest`,
`PaymentRefundServiceTest`, `PaymentIdempotencyTest`, provider tests for Mock,
MoMo, and PayPal, and `MockPaymentCommerceLifecycleIntegrationTest`.

Carrier coverage includes `CarrierServiceTest`, `CarrierCheckoutServiceTest`,
`AdminCarrierControllerTest`, `MockCarrierProviderTest`, and AhaMove mapper,
configuration, provider, and webhook tests.

Future runtime changes must update the relevant API contract in the same
delivery and add coverage for success, validation, authorization, conflict,
idempotency, and provider-failure paths. Changes involving carrier secrets or
payment credentials must preserve existing encryption, redaction, and webhook
signature controls.

---

## 4. Roadmap maintenance rule

When a carrier or payment feature is delivered, update this snapshot with the
implemented behavior and migration range, remove completed work from the
backlog, and link any newly confirmed gap to a GitHub Issue. Do not record
aspirational behavior as delivered.
