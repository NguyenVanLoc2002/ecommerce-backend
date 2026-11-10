# Product-search MVP performance evaluation

Issue: [#27](https://github.com/NguyenVanLoc2002/ecommerce-backend/issues/27)

## Purpose and decision rule

This runbook measures the existing MariaDB FULLTEXT product-search path before any
Elasticsearch decision. Elasticsearch is not a remediation for application-side
latency, N+1 loading, unsuitable indexes, or an unrepresentative test dataset.

The benchmark fails when any representative scenario has:

- error rate above 0%;
- p95 service latency above 300 ms; or
- p99 service latency above 600 ms.

Those are server-side budgets for a catalog-list request during a peak-like burst;
they leave time for browser, CDN/gateway, and network latency in an ecommerce page
experience. They are a release gate for this repeatable dev benchmark, not a claim
about production capacity.

## Workload

- MariaDB 11.4 in an isolated Testcontainers database, matching the Compose major/minor version.
- 100,000 synthetic published products, each with a FULLTEXT `search_text` document,
  one active variant, and one primary product image. This includes the relationships
  used when mapping an actual catalog-list response.
- 100 simultaneous service callers; each makes ten measured requests after warm-up.
- Page 0, 20 results, newest-first sort.
- Four scenarios: common keyword, common keyword with `featured`, rare keyword,
  and no-result keyword.

The measurement includes the service search route, native FULLTEXT/count queries,
entity hydration, and list-item mapping. It excludes external HTTP/network hops,
so staging monitoring remains necessary before treating it as a production SLO.

## Run

```powershell
mvn -q "-Dmaven.repo.local=target/.m2repo" "-Dsearch.performance.enabled=true" "-Dtest=ProductSearchPerformanceIntegrationTest" test
```

The test is opt-in and therefore does not run in the normal Maven suite. Its output
contains one `SEARCH_PERF` record per scenario with p50/p95/p99/max latency and
error count. Optional JVM properties can tune catalog size, concurrency, requests,
or budgets for a separate capacity experiment; any changed values must be recorded
with the result.

## Results: 2026-11-10 isolated dev run

Command:

```powershell
mvn -q "-Dmaven.repo.local=target/.m2repo" "-Dsearch.performance.enabled=true" "-Dtest=ProductSearchPerformanceIntegrationTest" test
```

Environment: MariaDB 11.4 Testcontainers, Docker Desktop with 11.9 GB available
memory, catalog size 100,000, 100 concurrent callers, Hikari maximum pool size 32,
100 warm-up requests per scenario, and 1,000 measured requests per scenario.

| Scenario | p50 | p95 | p99 | Errors | Gate |
| --- | ---: | ---: | ---: | ---: | --- |
| Common `ao thun` | 54 ms | 562 ms | 740 ms | 0 | Fail |
| Common `ao thun` + `featured` | 57 ms | 447 ms | 668 ms | 0 | Fail |
| Rare `limited signature` | 5,991 ms | 16,788 ms | 23,211 ms | 2 | Fail |
| No-result phrase | 32 ms | 216 ms | 370 ms | 0 | Pass |

The two rare-keyword failures were `CannotCreateTransactionException: Could not
open JPA EntityManager for transaction`; their maximum observed latency was 31,712
ms. The query plan used `ftx_products_search` (`type=fulltext`) but reported
`Using temporary; Using filesort` for relevance ordering.

### Decision

The current MVP path does not meet the selected high-peak latency budget. This does
not justify adding Elasticsearch yet: the evidence also shows application connection
acquisition failure under burst load and relational query work after the FULLTEXT
lookup. Elasticsearch would not resolve those conditions by itself.

Before a separate Elasticsearch design Issue can be justified, create and validate
a focused optimization follow-up that measures: (1) a bounded/seek pagination or
count strategy for broad keyword matches, (2) relevance-sort query plans, (3)
Hikari pool capacity and database concurrency using staging-equivalent resources,
and (4) catalog-list relation loading. Re-run this benchmark after each isolated
change. No production inference is made from this dev-container run.
