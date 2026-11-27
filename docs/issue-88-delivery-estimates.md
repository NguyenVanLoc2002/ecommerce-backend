# Issue #88 · Checkout delivery estimates

The previous city/district policy fallback has been removed. New delivery estimate rules use Backend province and ward codes, approved carrier service and warehouse, an effective period, a separate carrier calendar and a coverage reference.

Checkout continues to use the current provider name, service and priced fee. It omits new ETA values pending the coordinated two-level address cutover, provider validation and quote snapshot work. Order snapshots already persisted through V59 are read as recorded.

Ahamove v3 returns service and fee. The adapter does not interpret order_time or duration as delivery ETA. A real sandbox response is needed to confirm the reference event, time units and meaning of any ETA field. [Ahamove v3 Estimate Order Fee](https://developers-stg.ahamove.com/en/docs/api-reference/order-apis/estimate-order-fee)

See [delivery estimate rules](delivery-estimate-rules.md), [operating calendars](shipping-calendar.md) and [shipping policy publication](shipping-policy.md).
