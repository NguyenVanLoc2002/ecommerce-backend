# Catalog presentation

The Size Guide and product badge features provide pre-purchase content for Issue #33. They do not participate in order, payment, tax, shipping quotation or inventory calculations.

## Persistence and delivery

- Flyway V57 adds reusable `size_guides` and explicit `size_guide_assignments`. The primary scope key represents exactly one target tuple and its foreign keys protect catalog links. Cells are bounded JSON because column structures vary and individual cells are not searched. No official size chart is seeded.
- V58 adds badge definitions and a product-to-badge relation, with a composite primary key to prevent duplicate assignments, an inverse index for Admin traversal and a unique code. It seeds the approved starter definitions without linking them to products.
- Product detail enriches the existing catalog response with one indexed guide lookup and one batch badge lookup for one product. The guide query resolves candidates from the current product and indexed category memberships, joins assignments by primary key and returns one document. Ranking respects scope precedence, assignment priority, category sort order, slug and finally stable scope key. Product list adds one grouped rank query restricted to IDs in the requested page and returns at most one small badge per item. It does not read guide content.
- Enrichment is outside the Redis product-detail snapshot. Updating a guide, badge or assignment is reflected on the next server request without flushing catalog cache entries. The original cached product response remains compatible. Admin routes require roles already used for catalog administration. Public responses include selected content for published products and no internal assignment rows.
- Additional SQL round trips are bounded by route. Payload growth is one optional guide with up to 12 columns and 40 rows, or one/two small badge summaries. Customer Web sends no new request per PDP or Product Card. Relative database latency at production cardinalities is pending measurement.

## Catalog content and dependencies

- Product media, descriptions and brand continue using catalog data. The prototype Maison Aure is illustrative. There is no seeded lookbook text, time estimate, tax rate or shipping promise.
- The invoice service copies order subtotal, discount, shipping fee and total. Current DTOs do not expose per-product VAT applicability or a reliable inclusive-tax indicator. A VAT label requires a confirmed mapping from the responsible tax/invoice source. Cart, order and invoice formulas remain untouched.
- The current checkout quote returns `shippingFee`, currency, carrier and service information. Its DTO has no ETA. The shipment ETA is available later in tracking where present. Customer Web renders a general checkout quote note together with applicable return policy. Specific delivery promises, policy-page copy and checkout ETA remain dependent on an approved policy and a trustworthy provider estimate.

## Backlog

**Lookbook and Collection.** Confirm ownership, editorial workflow, publishing and CTA destination. Confirm whether collections require their own catalog entity or an existing category route. Supply approved imagery and content before enabling the existing reusable PDP story section on production. The product discovery query continues using categories.
