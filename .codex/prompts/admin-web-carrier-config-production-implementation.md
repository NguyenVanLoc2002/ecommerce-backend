# Admin Web Carrier Config Production Implementation

You are implementing the admin web UI for carrier configuration against the current backend in this repository.

Read first:

- `docs/admin-api-contract.md`
- `docs/carrier-integration-flow.md`
- `docs/carrier-config-admin-ui-spec.md`

Use the backend contract as the source of truth. Do not invent endpoints or payload fields.

## Goal

Implement a production-facing carrier configuration experience for AhaMove without exposing raw JSON editing as the primary UX.

## Backend endpoints to use

Carrier catalog:

- `GET /api/v1/admin/carriers`
- `GET /api/v1/admin/carriers/{id}`
- `POST /api/v1/admin/carriers`
- `PATCH /api/v1/admin/carriers/{id}`
- `PATCH /api/v1/admin/carriers/{id}/toggle`

Typed AhaMove integration:

- `GET /api/v1/admin/carriers/{id}/integration/ahamove`
- `PUT /api/v1/admin/carriers/{id}/integration/ahamove`
- `POST /api/v1/admin/carriers/{id}/integration/ahamove/test-connection`
- `POST /api/v1/admin/carriers/{id}/integration/ahamove/webhook-token`
- `GET /api/v1/admin/carriers/{id}/integration/ahamove/webhook-setup`

Legacy endpoint:

- `PUT /api/v1/admin/carriers/{id}/config`

Do not use the legacy `/config` endpoint as the main AhaMove setup UX. It exists for backward compatibility only.

## UI scope

### 1. Carrier list

Build a `Shipping Providers` page that shows:

- carrier name
- provider type
- active/inactive catalog status
- config enabled flag
- connection status
- last health check time
- quick actions:
  - view details
  - configure
  - toggle active state

Show `connectionStatus` from `CarrierResponse` when available.

### 2. Carrier detail/config drawer or page

For `providerType = AHAMOVE`, render a structured form instead of a raw JSON textarea.

Fields:

- API key
- Secret key
- Webhook secret
- Base URL
- Config enabled
- Account phone
- Brand name
- Pickup address
- Pickup short address
- Pickup contact name
- Pickup contact phone
- Pickup latitude
- Pickup longitude
- Default service code
- Default payment method

Rules:

- treat secret inputs as write-only
- if `hasApiKey` or `hasWebhookSecret` is true, show badge like `Saved`, not raw values
- if the user leaves a secret field empty, do not overwrite it
- show `maskedWebhookToken` when present
- do not render `configJson` as the primary form

### 3. Save flow

On submit:

- call `PUT /api/v1/admin/carriers/{id}/integration/ahamove`
- send only fields defined by `UpdateAhamoveIntegrationRequest`
- show field-level validation errors when backend returns `VALIDATION_ERROR`

### 4. Test connection flow

Add a `Test connection` action.

Behavior:

- call `POST /api/v1/admin/carriers/{id}/integration/ahamove/test-connection`
- allow testing with current saved config
- optionally allow testing after local edits by sending:
  - `apiKey`
  - `baseUrl`
  - `phone`

Render result clearly:

- success/failed state
- backend message
- resolved base URL
- resolved phone
- updated connection status
- last health check timestamp if the screen refreshes data

### 5. Webhook setup flow

Add a `Webhook setup` section with two actions:

- `Generate token`
- `View setup instructions`

Generate token:

- call `POST /api/v1/admin/carriers/{id}/integration/ahamove/webhook-token`
- show the raw token exactly once in a secure confirmation modal
- warn the user that it will not be shown again

View setup instructions:

- call `GET /api/v1/admin/carriers/{id}/integration/ahamove/webhook-setup`
- display:
  - webhook URL
  - auth header
  - auth scheme if present
  - masked token
  - instruction list
- add copy buttons for webhook URL and token when the raw token is still available in current UI state

### 6. Legacy compatibility

If the backend still returns `configJson`, treat it as:

- optional debug data
- hidden behind an advanced accordion
- read-only by default

Do not require the admin user to type JSON manually.

## UX requirements

- prioritize a setup wizard feel over a generic CRUD form
- separate:
  - catalog metadata
  - provider credentials
  - pickup defaults
  - webhook setup
  - connection health
- clearly explain:
  - `catalog status` vs `config enabled`
  - secret values are never returned by backend
  - webhook token must match the AhaMove partner portal setup

## Error handling

Handle these carrier errors explicitly:

- `CARRIER_NOT_FOUND`
- `CARRIER_CONFIG_MISSING`
- `CARRIER_CONFIG_DISABLED`
- `CARRIER_REQUEST_FAILED`
- `CARRIER_PROVIDER_NOT_SUPPORTED`

Do not display raw backend stack traces or sensitive payloads.

## Acceptance criteria

- no raw JSON is required for standard AhaMove onboarding
- admins can save typed config, test connection, rotate webhook token, and read webhook setup instructions
- UI reflects connection status and health check information
- secret handling remains write-only and safe
- implementation uses only documented backend endpoints above
