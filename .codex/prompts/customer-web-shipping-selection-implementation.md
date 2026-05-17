# Customer Web Shipping Selection Implementation

You are implementing customer checkout carrier selection against the current backend in this repository.

Read first:

- `docs/customer-api-contract.md`
- `docs/admin-api-contract.md`
- `docs/carrier-integration-flow.md`

Treat the backend contract as the source of truth.

## Goal

Implement customer checkout shipping selection so the customer can:

- load available checkout carriers
- select one carrier
- preview shipping fee and totals
- create the order with the selected `carrierId`
- reconcile UI totals with the authoritative `OrderResponse`

## Backend endpoints to use

- `GET /api/v1/carriers/checkout-options`
- `POST /api/v1/orders/preview`
- `POST /api/v1/orders`

Do not invent public carrier APIs beyond these routes.

## Required flow

### 1. Load checkout carriers

When checkout loads and the customer has enough context for shipping estimation, call:

- `GET /api/v1/carriers/checkout-options`

Use the response to render:

- carrier name
- logo if present
- provider type if useful
- estimated fee if returned
- availability state

### 2. Preview order totals

Whenever the customer changes:

- shipping address
- cart contents
- voucher code
- selected carrier

call:

- `POST /api/v1/orders/preview`

Send the actual `carrierId` selected by the customer.

Render preview values from the server response, including:

- subtotal
- discount amount
- shipping fee
- total amount

Do not keep a client-only shipping fee formula when preview data exists from the backend.

### 3. Create order

When the customer confirms checkout:

- call `POST /api/v1/orders`
- include `carrierId` exactly as defined by backend contract

After create succeeds:

- replace preview totals with values from `OrderResponse`
- use the persisted order carrier snapshot returned by backend

### 4. Empty and fallback states

Handle these cases safely:

- no carriers returned
- preview fails
- selected carrier becomes unavailable
- order create returns a different authoritative shipping fee than the preview

If preview fails:

- show a clear error
- prevent checkout submission if the backend requires a valid carrier selection

## UI requirements

- shipping selection must be visible in checkout, not hidden in a later step
- total summary must update after every successful preview
- the selected carrier should remain stable when the user navigates between checkout sections
- show loading and disabled states during preview mutation

## Error handling

Handle these backend errors explicitly:

- `VALIDATION_ERROR`
- `CARRIER_NOT_FOUND`
- `CARRIER_CONFIG_MISSING`
- `CARRIER_CONFIG_DISABLED`
- `CARRIER_REQUEST_FAILED`

Do not fake success if preview/order creation fails.

## Acceptance criteria

- customer can choose a carrier in checkout
- shipping fee and totals are derived from backend preview data
- `carrierId` is sent on order creation
- final order summary is reconciled against persisted server response
- no client-only fake shipping calculation remains in the final flow
