# Issue #87 and #88 implementation sequence

Issue #87 owns tax groups and effective-dated rates, purchase and formal tax snapshots, invoice preparation and external issuance records, accounting export, and refund adjustments. Formal tax determination uses explicit, permissioned transfer evidence until the business confirms a supported event. Ecommerce invoice output and legal e-invoice issuance remain separate.

Issue #88 owns the shared Vietnam address catalog and two-level cutover, shipping policy publication, calendars and route rules, carrier estimate normalization, and quote validation. Existing district fields were found in Address, Order and Invoice snapshots, in Ahamove formatting, and in the Customer address form and Admin Order/Shipment/Invoice readers. Warehouse pickup still contains free text. Replace the write/read contract only as a coordinated cutover across the three repositories. Flyway history remains intact. A new schema migration can change the affected tables against an isolated test database. Order and invoice snapshots retain the names used when recorded.

Work in dependency order.

1. Build the authoritative province/ward catalog and public read endpoints. Implement versioned policy management in parallel with the independent tax calculation rules.
2. Switch Address Book, warehouse pickup, Order and Invoice snapshots, carrier formatting, and all TypeScript consumers in the same cutover. Validate membership from catalog codes and preserve address snapshot names.
3. Put tax group/rate administration, estimate calculation, and final determination behind independent permissions and mutually consistent snapshots. Reconcile refunds and invoice export from those snapshots.
4. Put calendar/route administration before policy ETA resolution. Verify quote expiry and reconfirmation on the server. Wire the Admin and Customer surfaces to proven contracts and run isolated E2E on desktop/mobile.
5. Record implementation, test and production-readiness status independently. The legal entity, formal tax event, real carrier evidence and operational approval gate production activation. Fixtures and mocked integrations are explicitly test evidence.

No applied migration is edited. No production data transition or legacy tax backfill is planned.
