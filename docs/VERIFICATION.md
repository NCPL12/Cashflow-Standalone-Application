# Source handoff verification — 5 November 2026

- Independent Maven verify build: SUCCESS.
- All 40 Cashflow Java tests passed (zero failures/errors).
- All 32 fresh-database packaged HTTP checks passed using the WAR built from this source.
- Verified admin/Finance accounts, authorization, CSRF, assets, host LAN interface,
  external Tally config, mock read-only sync, retention/export, unavailable Tally
  snapshot preservation, restart persistence and administrator reset preservation.
- No office Tally or live application was changed during extraction/tests.
- Windows packaging script parsed successfully. Installer templates are copied
  from the previously verified Finance installer; Java runtime is supplied by
  the developer when rebuilding the Windows package.
- Source scope: 63 Java files (Cashflow plus required security/role/coordinator
  support), Cashflow-only templates/static resources, tests, configuration and
  packaging source. No ERP stock/purchase-order export application source.

## Extraction changes

Cashflow/backend/UI files originate from the tested 5 November Finance release.
The POM/dependencies/resources are scoped to Cashflow, the default profile is
standalone, old commented example credentials were removed from UserService,
and the standalone ERP-link pipeline returns an informational unavailable result
instead of querying absent ERP operational tables. Two inherited test expectations
now reflect current snapshot-failure and cost-centre company-validation behavior.
The installed Finance package and running host were not modified.

Live Tally company connectivity still depends on the deployment PC's reachable
Tally address, port and exact company name. The handoff tests use a local mock.
