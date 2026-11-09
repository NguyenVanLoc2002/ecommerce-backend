# Legacy carrier `configJson` retirement

## Scope and inventory

`store_carrier_configs.config_json` is legacy AhaMove compatibility data. Its
known keys are `phone`, `brandName`, `webhookToken`, pickup address/contact/
coordinates, `groupServiceId`, `paymentMethod`, and optional `groupRequests`.

The typed destinations are `provider_account_phone`, `provider_brand_name`,
the carrier pickup fields, `default_service_code`, and
`default_payment_method`. A selected active warehouse remains authoritative
for pickup data. `webhookToken` belongs in encrypted `webhook_secret_enc`;
`groupRequests` has no typed persistence target and remains legacy-only while
it is unused by normal onboarding.

## Forward-only backfill and compatibility window

Flyway `V36` copies only non-sensitive, valid JSON values into empty typed
columns for `AHAMOVE` carriers. It never overwrites typed values, does not
parse invalid JSON, and does not copy the plaintext webhook token.

Raw `configJson` is no longer accepted or returned by normal admin APIs. The
database column and server-side resolver fallback remain during this
compatibility window so an existing integration can be recovered without a
destructive migration. Existing webhook tokens must be rotated through the
typed AhaMove endpoint before the fallback is removed.

Before a later removal migration, production rollout must inventory remaining
non-null JSON rows without logging values, rotate any legacy webhook tokens,
replace or explicitly retire `groupRequests`, prove typed quote/shipment and
webhook behavior, and then remove the fallback and column in a separately
reviewed forward-only migration. Rollback mitigation during this window is to
retain the legacy column and restore use of typed values that V36 did not
overwrite.
