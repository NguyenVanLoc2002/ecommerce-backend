# Order Management Contract and Safe Migration Proposal

**Status:** Proposal for review

**Issue:** [#53](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/53)

**Scope:** Design only. This document does not authorize an API, state-machine, payment-policy, shipping-policy, or schema implementation.

## 1. Purpose and verified current behavior

This proposal defines the contracts and rollout constraints for the order-management work that follows this Issue. The current backend is the source of truth for behavior:

- `Order` snapshots recipient and shipping-address fields at checkout, and keeps a required relation to `Customer`. `OrderServiceImpl.buildAdminListItemResponse` reads the customer's current user name and email for the Admin list.
- `Order.shippingFee` is set from the checkout carrier quote and included in the customer total. It represents the shipping amount charged to the customer.
- `Shipment.shippingFee` is accepted through shipment create/update requests, defaults to zero, and has no verified carrier-cost meaning. Provider create results currently do not carry a charge amount.
- `shipments.order_id` is unique: one shipment per order. This proposal does not change that constraint.
- `ShipmentServiceImpl` invokes provider booking inside the service transaction before the shipment has been durably persisted. A process or database failure after the provider accepts a booking can leave external and internal state inconsistent.
- `CarrierProvider` exposes create, cancel and tracking methods. Its current contract has no explicit idempotency-key or booking-lookup capability.

The issue's stated context and acceptance criteria govern this proposal. Any unresolved business choice is marked as a decision required before implementation rather than silently assigned a policy.

## 2. Proposed order and customer DTO contract

Keep three concepts distinct in order read models:

1. **Order-time customer snapshot:** immutable name and email captured at order creation, if the business confirms these are required historical facts. They describe the ordering customer at checkout, not the recipient.
2. **Current customer profile summary/link:** current name/email and `customerId`, explicitly labelled as current profile data. This remains useful for support navigation and must not be represented as historical order data.
3. **Recipient/address snapshot:** existing receiver name, phone, street, ward, district, city and postal code captured from the selected address at checkout. These fields describe delivery and remain independent of the customer profile.

Suggested response grouping (names are proposal-only until API review):

```json
{
  "customerId": "…",
  "customerAtOrder": { "name": "…", "email": "…", "snapshotAvailable": true },
  "currentCustomer": { "name": "…", "email": "…" },
  "recipient": { "name": "…", "phone": "…" },
  "shippingAddress": { "street": "…", "ward": "…", "district": "…", "city": "…", "postalCode": "…" }
}
```

For legacy orders without order-time customer fields, return `snapshotAvailable: false` and omit the unavailable snapshot values. Do not copy today's profile values into historical snapshot columns. A current profile may still be returned in the separately labelled `currentCustomer` object, subject to authorization. Existing fields should remain available during a compatibility window; removal or semantic change requires an explicitly reviewed API versioning/deprecation plan.

### PII access and audit

- Customer endpoints may return only data needed by the authenticated owner for their own order. Enforce ownership in the service/API authorization path.
- Admin support roles may see customer identity and recipient contact data only as required by their order-management duties. Apply existing admin role rules and avoid broadening access to unrelated roles.
- Never include full PII in application logs, metrics, provider error text, or audit payloads. Audit actor, action, order identifier, timestamp and changed field names; avoid duplicating raw values unless a separately approved audit requirement demands it.
- The repository documents no order-PII retention duration in the reviewed contracts. Before implementation, the product/data owner must specify retention, erasure/legal-hold handling, and access-audit retention. Do not invent a retention period in code or migration.

## 3. Shipping amounts and additive data plan

Use unambiguous meanings in future APIs and persistence:

| Field | Proposed meaning | Source |
| --- | --- | --- |
| `Order.shippingFee` | Amount charged to the customer for shipping and included in order total | Checkout quote/order pricing snapshot |
| `Shipment.carrierCost` | Actual amount charged by the carrier, only after confirmed evidence is available | Provider settlement/booking result or reconciled operator entry |
| `Shipment.estimatedCarrierCost` | Optional estimate; not an amount charged or settled | Provider quote/estimate |

Do not derive `carrierCost` from `Order.shippingFee`. Do not reinterpret existing `Shipment.shippingFee`: its meaning is unverified, and manually entered values may not be provider cost. Preserve the legacy field and values until a separately approved compatibility/deprecation plan can safely retire it.

Any later schema change must use a new forward-only Flyway migration. Candidate additive columns are nullable `carrier_cost DECIMAL(18,2)` and `estimated_carrier_cost DECIMAL(18,2)`; keep `NULL` as “unknown/not recorded,” distinct from zero. No backfill should populate these fields from legacy `shipping_fee` without row-level evidence proving equivalence. Preserve existing migrations and avoid a destructive rename/drop in the compatibility release.

Recommended rollout:

1. Review and approve field semantics, null handling, permissions and old-client behavior.
2. Add nullable columns and indexes only if query access patterns justify them; deploy readers that tolerate null and continue supporting legacy fields.
3. Deploy writers with source/provenance and timestamp where needed to distinguish provider-confirmed, estimated and manually reconciled values.
4. Observe production data quality and compatibility before considering legacy-field deprecation in a separate change.

Rollback/mitigation: application rollback remains possible while the additive columns stay unused. Do not roll back by dropping columns after they contain data; forward-fix or leave the additive schema in place.

## 4. Durable carrier booking attempts

The system must represent an external booking whose outcome is unknown. A timeout is not proof that the provider rejected the request.

Proposed durable attempt record (logical contract, not final table/API names):

- Internal attempt ID and shipment/order reference.
- Provider/carrier identity and stable client-generated idempotency key, when the provider supports one.
- Attempt state: `PENDING`, `SUCCEEDED`, `FAILED_FINAL`, `UNKNOWN`, with explicit retry/reconciliation timestamps and attempt count.
- Sanitized request fingerprint and non-secret correlation data; never persist credentials or unrestricted raw provider payloads.
- Provider booking ID/tracking identifiers on success, response classification, timestamps, and operator/audit metadata for manual recovery.

Persist the attempt and stable key before making the external call. Make provider calls outside a long-running database transaction where feasible, then persist the outcome in a short transaction. On timeout/connection loss, mark `UNKNOWN`; reconcile before retrying a non-idempotent booking. A retry must reuse the same provider idempotency key if supported. Do not create a second booking just because the first response was lost.

Cancellation is also an external side effect: record cancellation attempts and unknown outcomes; do not mark the shipment cancelled until provider confirmation or a documented operator resolution. Compensation (such as cancelling a booking after a later local failure) is best-effort and must itself be durable and retryable.

### Provider capability matrix

Capabilities below must be verified against the deployed provider integration/version before implementation. The current backend interface is evidence only for exposed adapter methods, not proof of remote API guarantees.

| Capability | Current generic backend contract | Implementation rule |
| --- | --- | --- |
| Idempotent booking | No key/capability is defined in `CarrierProvider` | Treat booking as non-idempotent until provider documentation and integration tests prove otherwise; persist our own attempt key regardless |
| Booking lookup by client key/provider ID | No explicit lookup method in the generic interface | If absent, do not blindly retry `UNKNOWN`; queue operator reconciliation |
| Cancellation | `cancelShipment` returns a boolean; provider support and semantics vary | Require confirmed response; model timeout/unknown separately and retry only under provider rules |
| Tracking/reconciliation | `getTracking` exists; webhooks exist for AhaMove | Tracking status is not necessarily proof that a booking was or was not created; reconcile booking identity separately |

Current AhaMove adapter evidence (source-level only; not a provider guarantee):

| Capability | Observed integration behavior | Limitation / recovery |
| --- | --- | --- |
| Booking idempotency | `AhamoveClient.createOrder` posts to `/v3/orders`; no idempotency key is visible in the request contract | Provider-side duplicate handling is unverified; an unknown response must not trigger blind retry. The [Create Order API](https://developers-stg.ahamove.com/en/docs/api-reference/order-apis/create-order) documents partner `tracking_number`, but not idempotent replay |
| Booking lookup | `getOrderInfo` reads `/v3/orders/{providerOrderId}`; the provider order ID is returned by successful create. The mapper sends `shipmentCode` as partner tracking number. AhaMove documents [lookup by internal tracking number](https://developers-stg.ahamove.com/en/docs/api-reference/order-apis/get-order-list-by-tracking-number), but this backend does not currently call it | Candidate reconciliation path: query by persisted shipment code and verify returned order before retrying. It is not yet implemented or verified against the configured account; if lookup cannot identify the attempt, escalate for manual reconciliation |
| Cancellation | Adapter calls DELETE by provider order ID, falling back to tracking number; client returns success when the HTTP call completes. AhaMove's [Cancel Order API](https://developers-stg.ahamove.com/en/docs/api-reference/order-apis/cancel-order) documents cancellation by either identifier for listed states | Confirm provider status eligibility and account behavior; remote retry guarantees remain unverified; timeout remains unknown |
| Tracking/reconciliation | Order info and tracking link are queried by provider ID; inbound webhooks deduplicate events and resolve by provider ID, then tracking number | Webhook and tracking data can reconcile when the provider order or shipment tracking identifier is known. Webhook callback logs are available in the AhaMove partner portal; a lost create response still requires lookup by our internal tracking number or manual recovery |

For each concrete provider, fill in a reviewed row with documentation link/version, request-key behavior, lookup semantics, cancellation window/terminal behavior, tracking latency, webhook guarantees, retry limits and manual recovery contact/runbook. Do not infer AhaMove guarantees solely from method names or current adapter code.

## 5. Carrier override, attention indicators and audit

An override must capture a non-blank reason, actor, timestamp, order/shipment, previous and selected carrier identifiers, and outcome. Keep secrets and unnecessary PII out of audit details. Authorization must be explicit and checked server-side.

Expose operational attention indicators derived from durable state, such as `BOOKING_UNKNOWN`, `CANCELLATION_UNKNOWN`, `CARRIER_INACTIVE`, `OVERRIDE_REQUIRES_REVIEW`, and `RECONCILIATION_OVERDUE`. These are indicators for staff action; they must not silently change order, payment, or shipment state. Define thresholds and ownership in an operations runbook before enabling alerts.

## 6. Sequence flows

These flows preserve current payment and order policies. Exact state transitions must be checked against `OrderStatus` and `ShipmentStatus` before implementation; this proposal does not authorize state-machine changes.

### ONLINE

1. Checkout snapshots the recipient/address and customer-order facts; calculate `Order.shippingFee` from the selected checkout quote and persist the customer total.
2. Create order in the existing online-payment state and initiate payment under current idempotency rules.
3. On verified successful payment callback, apply the existing payment/order transition idempotently; shipment booking remains governed by the existing fulfilment eligibility rule.
4. When eligible, create a durable booking attempt, then call the carrier. Persist confirmed identifiers and actual/estimated carrier amounts only when evidence supports them.

### COD

1. Checkout snapshots the same order facts and charges the customer the selected `Order.shippingFee` as part of the order total.
2. Preserve current COD order confirmation/fulfilment policy.
3. On fulfilment eligibility, persist a booking attempt before calling the carrier; record provider outcome independently from the customer shipping fee.

### Booking timeout or uncertain response

1. Attempt exists durably before provider call.
2. Timeout or lost response sets attempt `UNKNOWN`; do not create a duplicate shipment booking or assume failure.
3. If provider supports lookup/idempotency, reconcile using the stable key and persist the result. Otherwise surface an attention indicator and route to manual recovery.
4. Retry only after reconciliation establishes no booking exists or the provider guarantees idempotent replay.

### Inactive carrier

1. Validate carrier status before booking. An inactive carrier must not receive a new provider call.
2. Keep the order/payment state unchanged, return the established domain error, and surface an operational indicator if a previously created shipment depends on that carrier.
3. Carrier replacement, if allowed by product policy, requires an authorized override with reason and audit record.

### Carrier override

1. Authorize the actor and validate non-blank reason and target carrier availability.
2. Persist the override decision/audit before or atomically with the local selection change.
3. If an external booking already exists, do not silently replace it. Cancel/reconcile the old booking first according to provider capability, then book the replacement; keep unknown outcomes visible for manual resolution.

### Cancellation

1. Validate current order/shipment state and preserve existing payment/refund and inventory rules.
2. Persist a cancellation attempt before the provider call. Confirmed cancellation updates local shipment state; timeout becomes `CANCELLATION_UNKNOWN` until reconciled.
3. Apply customer payment refund/void and inventory effects only through existing domain workflows. Do not infer successful carrier cancellation from order cancellation alone.

### Late payment callback

1. Verify signature and deduplicate callback using existing provider transaction identity/idempotency behavior.
2. If the order has already been cancelled or reservation released, do not silently resurrect it or book a shipment. Route the payment to the existing conflict/refund/manual-review policy and record an attention indicator.
3. If a carrier booking is already confirmed, cancellation/compensation follows the durable cancellation process and provider capability rules.

## 7. Phase plan and dependencies

Issue #53 is foundational and has no declared blocking Issue. The following sequence is a proposal for the downstream Phase 1–6 work; each phase requires this document's review and explicit acceptance of unresolved policy choices.

| Phase | Scope | Depends on | Compatibility notes |
| --- | --- | --- | --- |
| 1 | Approve order identity, customer snapshot, recipient and PII access contract | Issue #53 review; product/data-owner retention decision | Additive response fields; legacy orders signal unavailable snapshot; no profile-to-history backfill |
| 2 | Additive carrier cost fields and migration | Phase 1 terminology review; data-owner approval of provenance | Keep `Order.shippingFee` and legacy `Shipment.shippingFee`; null means unknown |
| 3 | Durable provider booking/cancellation attempts and reconciliation | Phase 2 data model; provider capability matrix | Adapter capability is explicit; old/manual shipment path remains supported during rollout |
| 4 | Override reason/audit and operational attention indicators | Phase 3 attempt states; authorization/audit review | No silent carrier swap or state-machine change |
| 5 | Admin Web and Customer Web contract adoption | Backend DTO contract and rollout compatibility tests | Coordinate releases; label current profile vs order snapshot and customer fee vs carrier cost |
| 6 | Provider-specific operations, monitoring and recovery runbooks | Phases 3–5; each provider's verified capability evidence | Manual recovery required where idempotency/lookup is unavailable; production enablement is separate |

Do not treat these phase names as new GitHub Issues or as approval to implement them. Split and track them after proposal review.

## 8. Test and review plan for implementation phases

No tests are run by this design-only proposal. The later implementation should add:

- Service regression tests proving order-time snapshots do not change when a customer profile/address changes or is soft-deleted.
- Contract/security tests for customer ownership, admin-role PII access, missing legacy snapshots and separate current-profile labels.
- MariaDB migration integration tests with pre-existing orders and shipments, asserting legacy fees remain unchanged and new carrier costs remain `NULL` absent evidence.
- Provider adapter contract tests for idempotency, lookup, cancellation, timeout and tracking guarantees per provider.
- Booking lifecycle tests for durable-before-call, timeout-to-unknown, safe retry, reconciliation, duplicate callbacks, cancellation unknown, inactive carrier and override audit.
- Cross-repository API/type tests and focused UI tests for both Admin and Customer clients when Phase 5 is approved.
- Documentation checks for API examples, migration naming/order, provider evidence and operational recovery steps.

## 9. Decisions required before implementation

1. Which order-time customer facts are legally/business-required, and what are their retention and erasure rules?
2. Which Admin roles may view customer profile versus recipient PII, and which accesses/changes require audit?
3. What is the authoritative source and provenance for actual carrier cost, including manual reconciliation?
4. For each provider, what exact idempotency, lookup, cancellation and webhook guarantees are supported by the current account/API version?
5. What operational SLA and escalation path applies to `UNKNOWN` booking/cancellation outcomes?
6. What compatibility window and release order will Admin Web and Customer Web follow?

Until these decisions and this proposal are reviewed, keep implementation and migration work out of scope.
