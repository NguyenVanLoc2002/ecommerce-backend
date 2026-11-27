# Issue #88 · Checkout delivery estimates

## Configured SLA

This change supports an optional Backend estimate matched to one warehouse, carrier code, quoted provider service code, destination city and destination district. Operations must approve the route coverage and the business-day range before setting `app.shipping.delivery-sla.enabled=true`. Set rules using `app.shipping.delivery-sla.rules[n]` with keys `carrier-code`, `service-code`, `pickup-warehouse-id`, `destination-city`, `destination-district`, `min-business-days` and `max-business-days`. City, district and codes use exact matching after trimming and case normalization. Configure a district only if the range is valid for its service coverage. A missing field or nonmatching route produces no estimate. Duplicate configured routes and malformed ranges fail configuration on startup. No rule is seeded.

The estimate is expressed as a range of business days after carrier handover. Order handling time is separate. The Backend derives the route from the provider quote, configured warehouse and selected address. The client never submits an estimate. Each order preview and create request uses its current inputs and the existing quotation flow. Changing the cart, address, or carrier requires a new preview. Pricing, voucher allocation, payment amounts, booking and inventory use their existing inputs.

V59 adds three nullable snapshot columns to orders. They record the service code and range from the quote used during order creation. Orders without a valid estimate keep the fields null. A later configuration change does not update a saved snapshot. Replaying an idempotent request returns the saved order. The configuration lookup runs in memory and makes no additional carrier call or SQL query.

Before enabling storefront ETA, verify that editing an address in place invalidates the old preview. The current storefront preview key includes the address ID, cart and carrier. The address update hook invalidates address queries. Clear cached ETA during a refetch and show the range only for the current preview.

## Provider and publication gates

Ahamove estimate v3 returns the quoted service and fee, which continue to drive the quote. The adapter does not read `duration` into delivery ETA. The public example shows `order_time` in the request. Verification still requires a real sanitized sandbox response and confirmation of the unit and start event. Shipment tracking's estimated delivery date is a different lifecycle field. [Ahamove v3 Estimate Order Fee](https://developers-stg.ahamove.com/en/docs/api-reference/order-apis/estimate-order-fee)

Operations must approve the public policy content and route rules before enabling presentation. The planned shop copy covers nationwide shipment within carrier coverage, one working day of order handling, express where offered, local/inter-province/remote delivery, and quotes at checkout. Figures of 1–2, 2–5 and 3–7 business days are draft policy examples after handover. Each checkout response must use an approved rule for that route or a future verified provider ETA. The storefront can continue to show name and quoted fee without an ETA. Returns stay on the existing 14-day workflow and per-product return policy.
