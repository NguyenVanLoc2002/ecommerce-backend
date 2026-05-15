# Payment Integration — Implementation Guide

> **Last updated:** 2026-05-15  
> **Scope:** MoMo Wallet, PayPal Orders API v2, COD — flow, security, idempotency, caveats

---

## 1. Architecture Overview

```
                         PaymentProviderRegistry
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   MomoPaymentProvider  PaypalPaymentProvider  MockPaymentProvider
     (ConditionalOnProperty)  (ConditionalOnProperty)  (dev only)
```

Payment gateway integration follows the **Strategy pattern**:

| Component | Role |
|---|---|
| `PaymentProvider` (interface) | Gateway contract: `createPayment`, `capturePayment`, `verifySignature`, `isSuccess`, `extractOrderCode`, `extractProviderTxnId`, `extractAmount` |
| `PaymentProviderRegistry` | Auto-discovers all `PaymentProvider` beans, looks them up by `getProviderName()` |
| `PaymentServiceImpl` | Business orchestration (COD + online initiate + capture + callback) |
| `PaymentWebhookServiceImpl` | Webhook entry point — signature verify → idempotency → state mutation |
| `PaymentWebhookLogPersister` | Commits the initial webhook log in `REQUIRES_NEW` — survives outer rollback |

All payment gateway beans are `@ConditionalOnProperty` — a provider is only registered when its `enabled=true` flag is set in config.

---

## 2. Data Model

### Payment (one per order)

```
payments
├── id (UUID PK)
├── order_id (UNIQUE FK)
├── payment_code (UNIQUE, e.g. PAY20260514XXXX)
├── method (COD | ONLINE)
├── status (PENDING | INITIATED | PAID | FAILED | REFUNDED | PARTIALLY_REFUNDED | CANCELLED)
├── amount DECIMAL(18,2)
├── paid_at
├── expired_at
├── provider_order_id   ← MoMo: "MOMO_{orderCode}_{ts}", PayPal: PayPal order ID
├── provider_request_id ← MoMo: "REQ_{paymentCode}_{ts}", PayPal: same as provider_order_id
└── version (optimistic lock)
```

### PaymentTransaction (immutable audit trail, many per payment)

```
payment_transactions
├── transaction_code (UNIQUE)
├── status (INITIATED | SUCCESS | FAILED | REFUNDED | CANCELLED)
├── amount
├── method
├── provider (MOMO | PAYPAL | COD | null)
├── provider_txn_id   ← MoMo: transId, PayPal: capture ID
├── reference_type    (CALLBACK | CAPTURE | WEBHOOK)
├── reference_id      (orderCode)
└── payload           (raw webhook body, TEXT)
```

Every state change appends a new transaction row — never mutates existing ones.

### PaymentWebhookLog (every inbound webhook call)

```
payment_webhook_logs
├── provider
├── order_code
├── provider_txn_id
├── payload (raw body)
├── signature (raw header)
├── signature_valid (Boolean)
├── status (RECEIVED | PROCESSED | IGNORED | FAILED)
├── processed_at
└── error_message
```

Written in its own `REQUIRES_NEW` transaction — exists even when the outer transaction rolls back.

---

## 3. COD Flow

```
Customer places COD order
         │
         ▼
   OrderService.createOrder()
         │ calls internally
         ▼
   PaymentService.createCodPayment(order)
         │
         ├─► Payment{method=COD, status=PENDING} saved
         ├─► PaymentTransaction{status=INITIATED} appended
         │
         ▼
   [delivery happens]
         │
         ▼ (admin action)
   POST /api/v1/admin/orders/{orderId}/complete-payment
         │
         ▼
   PaymentService.completeCodPayment(orderId)
         │
         ├─► Payment.status → PAID, paidAt = now
         ├─► Order.paymentStatus → PAID, paidAt = now
         └─► PaymentTransaction{status=SUCCESS} appended
```

**Idempotency:** if payment already `PAID`, returns existing record silently.  
**Guard:** non-`PENDING` status (e.g., `REFUNDED`) throws `PAYMENT_ALREADY_PROCESSED`.

---

## 4. MoMo Wallet Flow (captureWallet)

### 4.1 Initiate (Customer → Backend → MoMo)

```
POST /api/v1/payments/order/{orderId}/initiate
  Headers: Idempotency-Key: <uuid>
  Body: { "provider": "MOMO", "returnUrl": "..." }

         │
         ▼
  PaymentService.initiateOnlinePayment()
         │
         ├─ Idempotency gate (IdempotencyService)
         │    └─ Same key + same hash → return existing payment
         │
         ├─ Ownership check: order.customer == currentCustomer
         ├─ order.paymentMethod must be ONLINE
         │
         ├─ If no existing Payment → create Payment{status=INITIATED, expiredAt=+24h}
         │   If existing INITIATED/PENDING → return as-is (in-flight)
         │   If existing FAILED → reset to INITIATED, append new INITIATED transaction
         │   If existing PAID/REFUNDED → throw PAYMENT_ALREADY_PROCESSED
         │
         ├─ Calls MomoPaymentProvider.createPayment()
         │       │
         │       ├─ Validate amount: 1,000 ≤ amount ≤ 50,000,000 VND
         │       ├─ Build providerOrderId = "MOMO_{orderCode}_{currentTimeMillis}"
         │       ├─ Build requestId = "REQ_{paymentCode}_{currentTimeMillis}"
         │       ├─ Build HMAC-SHA256 signature over fixed field order (MoMo spec)
         │       └─ POST https://test-payment.momo.vn/v2/gateway/api/create
         │               → returns { payUrl, deeplink, qrCodeUrl, resultCode }
         │               → resultCode != 0 → throw PAYMENT_FAILED
         │
         ├─ Save payment.providerOrderId, payment.providerRequestId
         ├─ Mark IdempotencyKey COMPLETED
         │
         └─► Response: { paymentUrl, deeplink, qrCodeUrl, ... }
```

**Customer is then redirected to** `payUrl` (web) or `deeplink` (app).

### 4.2 IPN Webhook (MoMo → Backend)

```
POST /api/v1/webhooks/payment/MOMO
  Body: MoMo IPN JSON (contains signature field)
  Header: X-Signature (optional, body field takes priority)

         │
         ▼
  PaymentWebhookServiceImpl.receiveWebhook("MOMO", rawBody, signature)
         │
         ├─ PaymentWebhookLogPersister.createInitialLog()  ← REQUIRES_NEW tx
         │    └─ Persists log{status=RECEIVED} regardless of outer outcome
         │
         ├─ MomoPaymentProvider.verifySignature(rawBody, signature)
         │       ├─ Parse IPN JSON → check partnerCode matches config
         │       ├─ Extract signature from JSON body (ignores X-Signature header)
         │       └─ HMAC-SHA256 over IPN fields (alphabetical order) vs stored secretKey
         │            → fail → log{status=FAILED} → throw PAYMENT_WEBHOOK_SIGNATURE_INVALID
         │
         ├─ extractOrderCode() → parse "MOMO_{orderCode}_{ts}" → recover orderCode
         ├─ extractProviderTxnId() → field "transId"
         │
         ├─ Idempotency guard: providerTxnId already in payment_transactions → log IGNORED
         │
         ├─ Load Order by orderCode → findByOrderIdWithLock (pessimistic write lock)
         │
         ├─ Amount guard: IPN amount vs payment.amount (BigDecimal compare)
         │    → mismatch → log FAILED → throw PAYMENT_CALLBACK_INVALID
         │
         ├─ Guard: payment already PAID → log IGNORED
         ├─ Guard: payment REFUNDED → log IGNORED
         │
         ├─ isSuccess() → resultCode == 0
         │    SUCCESS → payment.status=PAID, order.paymentStatus=PAID, paidAt=now
         │    FAILED  → payment.status=FAILED, order.paymentStatus=FAILED
         │
         ├─ PaymentTransaction{status=SUCCESS/FAILED, referenceType=WEBHOOK} appended
         ├─ WebhookLog.status=PROCESSED, processedAt=now
         │
         └─► HTTP 204 No Content (MoMo IPN spec requires this)
```

### 4.3 HMAC Signature Fields

**Create-payment request** (field order is fixed and alphabetical per MoMo spec):
```
accessKey=...&amount=...&extraData=...&ipnUrl=...&orderId=...&orderInfo=...
&partnerCode=...&redirectUrl=...&requestId=...&requestType=...
```

**IPN callback** (different field set, still alphabetical):
```
accessKey=...&amount=...&extraData=...&message=...&orderId=...&orderInfo=...
&orderType=...&partnerCode=...&payType=...&requestId=...&responseTime=...
&resultCode=...&transId=...
```

Both use `HMAC-SHA256` with `secretKey` as the key. Output is lowercase hex.

---

## 5. PayPal Flow (Orders API v2)

PayPal uses a **two-step authorize-then-capture** model — unlike MoMo which completes in one step via IPN.

### 5.1 Initiate (Customer → Backend → PayPal)

```
POST /api/v1/payments/order/{orderId}/initiate
  Headers: Idempotency-Key: <uuid>
  Body: { "provider": "PAYPAL", "returnUrl": "..." }

         │
         ▼
  PaymentService.initiateOnlinePayment()  [same gate as MoMo above]
         │
         ├─ Calls PaypalPaymentProvider.createPayment()
         │       │
         │       ├─ PaypalOAuthClient.getAccessToken()
         │       │       ├─ Cache check (in-memory, volatile, double-checked locking)
         │       │       └─ POST /v1/oauth2/token (Basic clientId:clientSecret)
         │       │            → caches token until expiresIn - 60s
         │       │
         │       ├─ resolvePaypalAmount(orderAmount):
         │       │       └─ currency=USD + testConversionEnabled=true
         │       │            → amount = orderAmount / testConversionRateVndToUsd
         │       │          (for production: direct passthrough with scale=2)
         │       │
         │       ├─ POST /v2/checkout/orders  (Prefer: return=representation)
         │       │    Body: {
         │       │      intent: "CAPTURE",
         │       │      purchase_units: [{ referenceId: orderCode, customId: orderCode, amount: ... }],
         │       │      payment_source: { paypal: { experience_context: {
         │       │          returnUrl, cancelUrl, userAction:"PAY_NOW",
         │       │          paymentMethodPreference:"IMMEDIATE_PAYMENT_REQUIRED",
         │       │          shippingPreference:"NO_SHIPPING"
         │       │      }}}
         │       │    }
         │       │    → response: { id: paypalOrderId, status, links: [{rel:"payer-action", href:...}] }
         │       │
         │       └─ Extract approval URL from links[rel="payer-action"]
         │
         ├─ Save payment.providerOrderId = paypalOrderId
         │
         └─► Response: { paymentUrl: approvalUrl, ... }
```

**Customer is redirected to** `approvalUrl` on PayPal site.  
After approving, PayPal redirects back to `returnUrl?token={paypalOrderId}`.

### 5.2 Capture (Customer returns → Frontend → Backend → PayPal)

```
POST /api/v1/payments/order/{orderId}/capture
  Body: { "provider": "PAYPAL", "providerToken": "{paypalOrderId}" }
  Auth: Bearer (customer must own the order)

         │
         ▼
  PaymentService.captureOnlinePayment()
         │
         ├─ Ownership check: order.customer == currentCustomer
         ├─ findByOrderIdWithLock (pessimistic write lock)
         │
         ├─ Idempotent: already PAID → return silently
         ├─ Guard: status not INITIATED/PENDING → throw PAYMENT_ALREADY_PROCESSED
         │
         ├─ Token verification: providerToken must equal payment.providerOrderId
         │    → mismatch → throw PAYMENT_CALLBACK_INVALID (substitution attack prevention)
         │
         ├─ PaypalPaymentProvider.capturePayment(payment, providerToken)
         │       └─ POST /v2/checkout/orders/{paypalOrderId}/capture
         │            → response: { purchase_units[0].payments.captures[0]: { id, status } }
         │            → success = (capture.status == "COMPLETED")
         │            → returns PaymentProviderCaptureResult{ success, providerTxnId=captureId }
         │
         ├─ SUCCESS → payment.status=PAID, order.paymentStatus=PAID, paidAt=now
         │   FAILED  → payment.status=FAILED, order.paymentStatus=FAILED
         │
         ├─ PaymentTransaction{referenceType=CAPTURE, providerTxnId=captureId} appended
         │
         └─► Response: PaymentResponse (with updated status)
```

### 5.3 PayPal Webhook (Reconciliation)

```
POST /api/v1/webhooks/payment/PAYPAL
  Headers: PAYPAL-AUTH-ALGO, PAYPAL-CERT-URL, PAYPAL-TRANSMISSION-ID,
           PAYPAL-TRANSMISSION-SIG, PAYPAL-TRANSMISSION-TIME
  Body: PayPal webhook event JSON

         │
         ▼
  PaymentWebhookController.handlePaypalWebhook()
         │  Serializes the 5 headers into a JSON string → passes as "signature" param
         │
         ▼
  PaymentWebhookServiceImpl.receiveWebhook("PAYPAL", rawBody, headersJson)
         │
         ├─ PaymentWebhookLogPersister.createInitialLog()  ← REQUIRES_NEW
         │
         ├─ PaypalPaymentProvider.verifySignature(rawBody, headersJson)
         │       ├─ Parse headersJson → extract 5 header values
         │       └─ POST /v1/notifications/verify-webhook-signature
         │            Body: { auth_algo, cert_url, transmission_id, transmission_sig,
         │                    transmission_time, webhook_id, webhook_event: <parsed body> }
         │            → verificationStatus == "SUCCESS" → valid
         │
         ├─ extractOrderCode() → resource.custom_id (set to orderCode at create time)
         │    → null for non-capture events (e.g. CHECKOUT.ORDER.APPROVED) → log IGNORED
         │
         ├─ extractProviderTxnId() → resource.id (the capture ID)
         ├─ extractAmount() → returns null (VND/USD mismatch, amount guard skipped)
         │
         ├─ [same idempotency + lock + state machine as MoMo webhook above]
         │
         ├─ isSuccess() → eventType == "PAYMENT.CAPTURE.COMPLETED" AND resource.status == "COMPLETED"
         │
         └─► HTTP 200 (PayPal webhook spec requires 200, not 204)
```

**Note:** If capture was already done via step 5.2, the `PAYMENT.CAPTURE.COMPLETED` webhook arrives with a `providerTxnId` that already exists in `payment_transactions` → idempotency guard fires → log `IGNORED`. No duplicate mutation.

---

## 6. Endpoint Reference

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/payments/order/{orderId}/initiate` | Bearer (CUSTOMER) | Initiate online payment. Requires `Idempotency-Key` header. |
| `POST` | `/api/v1/payments/order/{orderId}/capture` | Bearer (CUSTOMER) | Capture PayPal payment after customer approval. |
| `GET` | `/api/v1/payments/order/{orderId}` | Bearer (CUSTOMER) | Get payment status for own order. |
| `POST` | `/api/v1/webhooks/payment/MOMO` | None (public) | MoMo IPN callback. Returns 204. |
| `POST` | `/api/v1/webhooks/payment/PAYPAL` | None (public) | PayPal webhook. Returns 200. |
| `POST` | `/api/v1/webhooks/payment/{provider}` | None (public) | Generic provider webhook (VNPay, ZaloPay, etc.). Returns 204. |

---

## 7. Security Mechanisms

### 7.1 MoMo Signature (HMAC-SHA256)
- Signature is embedded **inside the IPN JSON body** as the `signature` field.
- `X-Signature` header is checked only if the body field is absent.
- Additional guard: `partnerCode` in the IPN must match our configured `partnerCode`.
- Failing signature → `PAYMENT_WEBHOOK_SIGNATURE_INVALID`, log status = `FAILED`.

### 7.2 PayPal Signature (Webhooks API)
- 5 PayPal headers are forwarded to PayPal's own `POST /v1/notifications/verify-webhook-signature`.
- Requires `webhook-id` to be configured (`app.payment.paypal.webhook-id`).
- If `webhook-id` is blank → verification fails-safe (returns `false`).
- Any API error during verification → returns `false` (safe rejection, not exception propagation).

### 7.3 Capture Token Substitution Prevention
- At initiate time, `payment.providerOrderId` is stored.
- At capture time, the frontend-supplied `providerToken` is compared against stored `providerOrderId`.
- Mismatch → `PAYMENT_CALLBACK_INVALID`. Prevents a customer from substituting another order's token.

### 7.4 Concurrency Protection
- `paymentRepository.findByOrderIdWithLock()` uses `@Lock(PESSIMISTIC_WRITE)`.
- Prevents two concurrent capture or webhook calls from both passing the "not PAID" check.
- Backed by `@Version` optimistic lock on `payments.version` as a secondary guard.

### 7.5 Amount Guard (MoMo only)
- IPN `amount` is compared to `payment.amount` (exact `BigDecimal.compareTo`).
- Mismatch → reject with `PAYMENT_CALLBACK_INVALID`.
- PayPal skips this guard (`extractAmount()` returns `null`) because amounts are in USD while the DB stores VND.

---

## 8. Idempotency

| Scenario | Behavior |
|---|---|
| Same `Idempotency-Key` + same payload on initiate | `IdempotencyService` returns `COMPLETED` key → return existing payment |
| Payment already `INITIATED`/`PENDING` on re-initiate | Returns existing in-flight record |
| Payment already `PAID` on capture | Returns existing record silently |
| Webhook arrives with known `providerTxnId` | Log status = `IGNORED`, no mutation |
| Duplicate `PAYMENT.CAPTURE.COMPLETED` webhook after capture-on-return | `providerTxnId` guard → `IGNORED` |

---

## 9. Retry Flow (FAILED payment)

```
Payment status = FAILED
         │
         ▼
Customer calls POST /initiate again (new Idempotency-Key)
         │
         ▼
PaymentServiceImpl detects existing payment with status=FAILED
         │
         ├─ Resets status → INITIATED
         ├─ Sets new expiredAt = now + 24h
         ├─ Appends PaymentTransaction{status=INITIATED}
         └─ Calls provider.createPayment() again with new providerOrderId/requestId
```

Each retry gets a new `providerOrderId` (MoMo) with a fresh timestamp suffix, ensuring MoMo treats it as a new request.

---

## 10. Configuration Reference

### MoMo (`application-dev.properties`)

```properties
app.payment.momo.enabled=true
app.payment.momo.environment=TEST
app.payment.momo.partner-code=${APP_PAYMENT_MOMO_PARTNER_CODE}
app.payment.momo.access-key=${APP_PAYMENT_MOMO_ACCESS_KEY}
app.payment.momo.secret-key=${APP_PAYMENT_MOMO_SECRET_KEY}
app.payment.momo.create-url=https://test-payment.momo.vn/v2/gateway/api/create
app.payment.momo.redirect-url=${APP_PAYMENT_MOMO_REDIRECT_URL}
app.payment.momo.ipn-url=${APP_PAYMENT_MOMO_IPN_URL}
app.payment.momo.request-type=captureWallet
app.payment.momo.lang=vi
app.payment.momo.connect-timeout-ms=30000
app.payment.momo.read-timeout-ms=30000
```

> **IPN URL** must be a publicly reachable HTTPS URL. Use `ngrok` or similar for local development.

### PayPal (`application-dev.properties`)

```properties
app.payment.paypal.enabled=true
app.payment.paypal.client-id=${APP_PAYMENT_PAYPAL_CLIENT_ID}
app.payment.paypal.client-secret=${APP_PAYMENT_PAYPAL_CLIENT_SECRET}
app.payment.paypal.base-url=https://api-m.sandbox.paypal.com
app.payment.paypal.return-url=${APP_PAYMENT_PAYPAL_RETURN_URL}
app.payment.paypal.cancel-url=${APP_PAYMENT_PAYPAL_CANCEL_URL}
app.payment.paypal.webhook-id=${APP_PAYMENT_PAYPAL_WEBHOOK_ID}
app.payment.paypal.currency=USD
app.payment.paypal.test-conversion-enabled=true
app.payment.paypal.test-conversion-rate-vnd-to-usd=25000
```

`test-conversion-enabled=true` must only be used for sandbox. In production, set `currency=VND` (if PayPal supports it) or implement a real exchange rate service.

---

## 11. Known Issues and TODOs

### HIGH — Security

| # | Issue | Location | Impact |
|---|---|---|---|
| 1 | `processCallback()` in `PaymentServiceImpl` has no signature verification | `PaymentServiceImpl.java:422` (TODO comment) | The generic `/api/v1/payments/callback` endpoint accepts any payload. Not currently routed to by MoMo/PayPal (they use webhook endpoints), but must be fixed before any provider uses it. |
| 2 | PayPal amount guard is skipped | `PaypalPaymentProvider.extractAmount()` returns `null` | A tampered webhook could report a lower amount. Mitigated by the fact that capture is already done by the time the webhook arrives, but reconciliation relies purely on `PAYMENT.CAPTURE.COMPLETED` event type. |

### MEDIUM — Operational

| # | Issue | Location | Impact |
|---|---|---|---|
| 3 | PayPal OAuth token is in-memory only | `PaypalOAuthClient` | On app restart, the token is re-fetched. Acceptable for low traffic; for high availability clusters, the token should be shared via Redis. |
| 4 | No webhook replay / retry mechanism | `PaymentWebhookServiceImpl` | If the webhook processing fails after log creation but before state mutation, there is no automatic retry. MoMo retries IPNs multiple times; PayPal retries failed webhooks. Manual reprocessing requires replaying the raw payload. |
| 5 | `payment.expiredAt` is stored but not enforced | `Payment.expiredAt` | Expired payments are not automatically cancelled. A scheduled job should transition `INITIATED` payments past `expiredAt` to `CANCELLED`. |

### LOW — Developer Experience

| # | Issue | Location | Impact |
|---|---|---|---|
| 6 | VND→USD conversion is hardcoded test rate | `PaypalPaymentProperties.testConversionRateVndToUsd` | Production requires a real FX service or PayPal must be configured to accept VND directly. |
| 7 | MoMo `capturePayment()` returns `Optional.empty()` | `MomoPaymentProvider` (not overridden) | MoMo does not use the two-step capture flow. The capture endpoint would fail if called for a MoMo payment, but there is no guard at the controller level to prevent it. |

---

## 12. Provider Comparison

| Feature | MoMo | PayPal |
|---|---|---|
| Flow | Single-step (IPN after redirect) | Two-step (approve → capture) |
| Confirmation mechanism | IPN webhook (server push) | Capture API call + webhook reconciliation |
| Signature algorithm | HMAC-SHA256 (body field) | PayPal Webhooks API v1 (5 headers) |
| Currency | VND only | USD (test conversion for sandbox) |
| Capture endpoint | Not used | `POST /v2/checkout/orders/{id}/capture` |
| Auth mechanism | partnerCode + accessKey + secretKey | OAuth 2.0 client_credentials (cached) |
| Amount validation | `extractAmount()` → amount guard active | `extractAmount()` → returns null, guard skipped |
| App support | deeplink + QR code returned | Web redirect URL only |
| Min/Max amount | 1,000 – 50,000,000 VND | No explicit limit in code |

---

## 13. Adding a New Provider

1. Create `MyProviderPaymentProvider implements PaymentProvider` in `infrastructure/payment/myprovider/`
2. Annotate with `@Component` + `@ConditionalOnProperty(name = "app.payment.myprovider.enabled", havingValue = "true")`
3. Implement all required methods: `getProviderName`, `verifySignature`, `isSuccess`, `extractOrderCode`, `extractProviderTxnId`
4. Override `extractAmount()` if the provider embeds amount in the webhook
5. Override `capturePayment()` if the flow is authorize-then-capture
6. Add `MyProviderPaymentProperties` with `@ConfigurationProperties`
7. `PaymentProviderRegistry` auto-discovers the new bean — no other wiring needed
8. Add webhook endpoint mapping if the provider uses non-standard headers (like PayPal's 5 headers)
9. Test IPN signature verification with real sandbox credentials before enabling in production
