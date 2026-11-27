# Issue #87 · VAT-inclusive merchandise snapshots

## Implemented foundation

`VatRateSchedule` validates configured effective periods per tax group. The start date is included and the end date is excluded. The caller supplies the business tax date. A missing group or period resolves to empty. A configured zero rate remains distinguishable from an absent rate.

`MerchandiseVatSnapshot` consumes the existing merchandise gross and the eligible discount already allocated to that line. Discounted gross equals taxable base plus VAT. The caller supplies the currency, decimal scale and rounding rule. Gross amounts must fit that scale exactly so this calculation cannot silently adjust the amount PayPal, COD or another provider collects. Credits are split using cumulative amounts from the original snapshot. The return workflow remains responsible for the allowed quantity, credited gross and idempotency. The calculation covers merchandise only.

The example with 540,000 gross, 54,000 eligible discount and an 8% test rate produces 486,000 payable, 450,000 taxable base and 36,000 VAT. VND scale zero and HALF_UP in the fixtures are proposed test inputs. Release configuration depends on the approved accounting rules.

## Persistence and rollout decision

The current order line already records the voucher allocation, gross line total and discounted gross (`netLineAmount`). Invoice currently copies order totals. Refund allocates a cumulative merchandise credit based on the original order line. Wire VAT alongside these sources after approval. The calculator is isolated from production order, payment, invoice, refund and exchange paths in this implementation.

- Use configured tax groups with effective-dated rate rows. Resolve a product override or approved apparel default in one batch at order creation. Resolve shipping under its own rule. Use a unique start date per group and validate overlap under a lock on that group. Use the shop business tax date with an approved tax-point event.
- Store one optional immutable VAT snapshot per new order line in the same transaction as the order and voucher snapshots. Record the group/rate revision, tax date, currency, discounted gross, base, VAT and rounding policy. An order is ready for VAT presentation only when all required tax components have validated snapshots. Old orders keep absent tax snapshots. A forward-only migration can introduce the configuration and snapshot tables after the invoice representation and rounding are confirmed.
- Preserve Order and Payment payable amounts and existing provider conversions. Invoice reads purchase snapshots. Credits use the original rate and reconciliation balance. Exchange replacements, shipping refunds and tax adjustments require their own agreed treatment. Retried and concurrent refund operations must use the existing locked allocation and idempotency boundaries.
- Add optional public fields only after the relevant surface has approved data and a release gate. Admin configuration requires explicit permissions and audit. Keep new fields off the product list unless a later UI requirement proves they are needed.

Production activation depends on the entity's filing method, a redacted invoice, tax-group eligibility, and the rounding and adjustment cases for mixed rates and shipping. Use approved configuration following the applicable reduction rules. [Nghị định 174/2025/NĐ-CP](https://chinhphu.vn/?docid=214310&pageid=27160)
