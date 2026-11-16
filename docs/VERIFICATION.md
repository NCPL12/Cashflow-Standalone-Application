# Standalone repository verification - 16 November 2026

Combined the 13 November standalone release with the repository's existing
administrator protection, XLS/XLSX import and disabled-module changes.

`mvn -B -o -DforkCount=0 verify` passed 106 tests with no failures, errors or skips.
The build used JDK 21.0.11 and Maven 3.9.16. Changed JavaScript files passed Node
syntax checks. The two JavaScript retention checks passed for inclusive overview
totals, separated detail views, monthly rows and CSV exports.

The packaged-app smoke test uses fresh local H2 storage and a mock Tally endpoint.
It makes no requests to production Tally or the installed Finance database.
All 58 packaged-app checks passed, including startup, user management, restart and
administrator-reset persistence, retention separation/exports, automatic payment
term discovery, narration edits/removal, cached warnings and failure recovery.
The mock recorded only Export requests and no Tally writes.

## Historical release verification - 13 November 2026

Unit and packaged-app regression results are recorded in this release's final manifest.
Packaged tests cover three unconfigured invoice references, editing a percentage without
changing balance, removing terms, cache/offline warnings/recovery, login/users/restart,
retention independence, read-only Tally requests and unchanged other outstanding screens.
Live read-only reconciliation into an isolated local H2 database found nine ordinary
invoices with the supported pattern. Their deferred amounts reconciled invoice-by-invoice
and month-by-month, with no unresolved review cases in that run. Already allocated
BLR2026232 was not split again. Production Tally and installed databases were not changed.
The source and full installer exclude live snapshots, accounts and the live cache.
Graphs navigation remains hidden by the existing reversible shared CSS rule.

Final results: 85 Java tests, 58 packaged-app checks, and 12 live read-only reconciliation checks passed. Live matching ordinary invoice count: 9; review cases in that run: 0.
