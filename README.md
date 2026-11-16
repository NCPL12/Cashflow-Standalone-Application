# Neptune Cashflow Standalone — developer source

Latest standalone source: 13 November 2026 automatic Cash Plan payment terms release.
Published to this repository on 16 November 2026.
This is a complete standalone Maven project: Cashflow Java backend, HTML/CSS/JS,
retention, projections, Finance plans, Tally reader, account management, tests,
configuration, and Windows installer/launcher source. It does not include the ERP
purchase-order export, monthly stock reporting, operational ERP controllers, JSPs,
production database settings, compiled output, Java binaries or user databases.

## Build / Eclipse

Use a JDK and Maven 3.9.x. The release was built/tested with JDK 21.0.11; the
existing Java 8 source target and dependency versions are preserved. Updating
framework versions is a separate developer task.

```powershell
mvn clean verify
```

Output: `target/neptune-cashflow.war` (executable Spring Boot WAR).
First build requires access to Maven dependency repositories.
In Eclipse: File → Import → Maven → Existing Maven Projects → select this folder.
Main class: `com.ncpl.sales.SalesApp`. The default profile is `cashflow-standalone`.

## Run from source/build

Set your own credentials in the current PowerShell session for a NEW database:

```powershell
$env:CASHFLOW_BOOTSTRAP_ADMIN_USERNAME = 'your-admin-name'
$env:CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD = '<choose-a-strong-password>'
java -jar target/neptune-cashflow.war --spring.profiles.active=cashflow-standalone --spring.config.additional-location=./config/
```

Open `http://127.0.0.1:8081/login`. No default password is supplied. After stopping,
remove these environment variables. Existing databases retain their accounts;
bootstrap credentials are used only when there are no users. H2 schema is created
and updated automatically. No MySQL, SQL dump or database import is required.
Local data: `data/cashflow_db.mv.db`; logs/uploads remain local to the host.
Stop the app before backing up the entire data folder.

## Tally and office network

Edit `config/application-cashflow-standalone.properties` and restart. This
PROFILE-SPECIFIC filename is required so external settings override packaged ones.

- `cashflow.tally.server.url=http://127.0.0.1:9000`: Tally on the same host; replace
  127.0.0.1 with the Tally PC's reachable IP when it runs elsewhere.
- `cashflow.tally.company-name=`: blank uses current Tally company; set exact name
  to pin the destination. Cost-centre native reports require the exact company name. Tally must expose its HTTP/XML server and loaded company.
- `cashflow.tally.sync.enabled=true`: automatic read refresh, every 60000 ms.
- `server.address=0.0.0.0`, `server.port=8081`: other PCs use
  `http://HOST-PC-IP:8081/login`. Keep the host awake and app running.

Tally requests read reports; this project does not push POs or write Tally vouchers.
Requests are serialized and a failed sync preserves the last successful snapshot.
Admin → Manage users creates Administrator or Finance user accounts. Management
is restricted to admins, forms use CSRF tokens, and passwords use BCrypt hashes.
The Windows network helper permits TCP 8081 from the local subnet on Private/Domain
networks. It is run by the installer user when network access is required.

## Retention and scope

Current mapping is authoritative Tally membership below Current Assets > Sundry
Debtors > Retention Amounts, including child groups. Ledger names are not used
for a successfully refreshed snapshot. Stored flags persist across restarts.
Legacy snapshots retain old classification until the first successful new sync.
Ledger balances not allocated to bills appear separately without invented dates.
Credit adjustments keep their sign and reconcile the ledger total. Retention is
included in overview tiles, excluded from ordinary detail/cashflow and provided
in separate Retention only tables and exports.

The standalone invoice-link pipeline needs ERP operational GRN/PO/SO records and
returns an explicit informational unavailable result without those records. The
Cashflow module includes shared security/role/coordinator support only.

## Rebuild a Windows installer ZIP

`packaging/template` contains installer, launcher, stop/reset, firewall helper,
configuration, logo and Finance instructions as source; no Java/runtime or WAR.
Supply a runtime from the working Finance package (its runtime folder) or a
compatible JDK/JRE folder containing bin/java.exe AND bin/javaw.exe:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Installer.ps1 -RuntimePath 'C:\Neptune Cashflow Finance\runtime'
```

The script builds the WAR and creates a new timestamped folder and ZIP under
`dist`. It does not install, change shortcuts/firewall, start Java, or include any
existing data/users/logs. Install On This Laptop.cmd creates the shortcut and
prompts for the first administrator on the destination PC. Reset Administrator
Login.cmd preserves saved balances, plans and other user accounts.

## Verification and handoff

Java tests are under src/test. `verification/verify_package.py` (Python standard
library only) tests a packaged folder with bundled Java, a fresh H2 database,
mock Tally, accounts, retention/export, LAN interface, failure preservation,
restarts and admin reset. It does not contact production Tally. Example:

```powershell
python verification/verify_package.py --package 'C:\Neptune Cashflow Finance' --test-dir 'target/smoke-fresh'
```

Use a new test directory on each run. Installer structure/PowerShell/shortcut
checks are in verification/Verify-Installer.ps1; pass -PackagePath explicitly.
Actual office Tally availability and a second PC's firewall route must be checked
in the deployment network. Source ZIP contains no live company data or accounts.

## 12 November 2026 — retention separation

Overview receivable/payable/net tiles and bill counts include retention. Normal
detailed tables, aging/concentration and Excel exports exclude it. Retention only
shows and exports the dedicated retention records separately. Outstanding API
returns allTotal/allCount alongside the selected-view total/count.

Normal weekly/projected cashflow and Finance cash-plan/schedule exclude retention,
even when it has an expected date. The Cashflow Retention only button provides a
separate release view; retained bills require an expected release date to enter
that projection. Retention projections do not use bank opening/closing balances.
Forecast Excel exports enforce the selected view on the server as well as the UI.
No database migration or data correction is required. The November 5 source and
installer are retained separately as the backup.

For an old Windows Surefire parent-process checker timeout, run `mvn -DforkCount=0 verify`; the 12 November local verification used this option.

## 13 November 2026 — authoritative Tally retention group

NEPTUNE CASHFLOW - RETENTION GROUP MAPPING - 13 NOVEMBER 2026

New source: Balance Sheet > Current Assets > Sundry Debtors > Retention Amounts.
All child ledgers/subgroups are included, regardless of ledger names.
A misleading ledger name outside the group is NOT retention after sync.

Bill-wise amounts and separately labelled Unallocated retention balance rows
reconcile to ledger closing balances. Credit adjustments retain their sign.
Unallocated rows have no invented bill or due date; they are excluded from
normal cashflow and require an expected date in the retention release view.

STOP Cashflow on the host before applying this update. Extract the entire ZIP
and run Apply Retention Update.cmd. It locates the current install, backs up
app WAR, data and config, then replaces the application. Start the normal shortcut.
Click Sync Now and wait for SUCCESS before Finance checks the new mapping.
A missing/wrong retention group causes a clear sync error and preserves the
previous snapshot, rather than guessing. Old snapshot classification remains
until the first successful new-version sync.

Set cashflow.tally.retention-group=Retention Amounts in the PROFILE-SPECIFIC
config file if a different exact group name is needed. Default is Retention Amounts.
The group must be under Sundry Debtors > Current Assets.

Two nullable columns (retention_group_member, retention_balance_only) are added
automatically to cashflow_invoices by the embedded H2 schema updater. Existing
users, expected cash dates and Finance plans are preserved. No manual SQL needed.
Before rollback stop Cashflow; restore the backed-up WAR. New columns can remain.
Data backups are under the installed folder's backups directory.


## Monthly retention statement - 13 November 2026
Monthly Cash Flow Statement offers Normal cash flow and Retention only.
Normal monthly totals and manual adjustments exclude retention. Retention balances
remain visible separately, including unallocated amounts and signed adjustments.
Only explicit, current expected release/payment dates schedule retention into a month;
missing or expired dates require Finance scheduling. Invoice due dates are not assumed
retention release dates. Selected-view CSV exports preserve this separation.
Overview tiles continue to include retention. No additional schema changes for the
monthly statement. The cumulative update includes the prior group-mapping columns.
For an existing installation, stop the installed app, apply the update, restart and
Ctrl+F5. Users, data, Tally config and Finance schedules are preserved. Sync Now is
needed to refresh older snapshots to the authoritative retention group mapping.

## Automatic Tally payment terms — Finance Cash Plan only (13 November 2026)

Finance Cash Plan refresh now reads current sales-voucher narrations from the configured
Tally endpoint/company. It is no longer restricted to configured invoice numbers.
Supported automatic pattern: one consistent payable percentage of BASIC plus FULL GST.
Normal amount = original invoice total minus the unpaid basic holdback, preserving GST
and rounding. Several Supply/Sales basic ledger lines are summed. Dates for deferred
collections are never invented. Other formats, extra charges, duplicate allocations,
partial payments or changed outstanding amounts require review and are disclosed.
A GST-rate annotation alone is not treated as payment terms.

Scope: Finance Cash Plan amounts, that tab's CSV and invoice-schedule API only.
Overview/outstanding/other graph totals and original invoice records stay unchanged.
Graphs navigation remains hidden, and graph code/routes remain intact.

Operation: Sync Now for current balances, then refresh Finance Cash Plan. Narration-only
changes are detected by refresh even when the outstanding balance has not changed.
The page and CSV state whether terms were read from Tally or an older cache was used.
If Tally is unavailable/busy, a visible warning prevents cached data being presented as
freshly verified. Without a matching successful cache, only explicitly configured rules
are applied and the warning states other invoices may still include deferred amounts.
Do not treat a warning-state cash plan as a fully refreshed Finance report.

Reads are Export-only and serialized with other app Tally activity. Metadata has no
inventory or full voucher children. Only matching candidate vouchers need minimal
ledger reads, in batches of at most five; unchanged MasterID/AlterID/narration entries
reuse cached amounts for up to five minutes. Edited or removed comments invalidate
the active split on refresh. No bulk inventory export or Tally import is added.
Standard Sales vouchers are supported. Ambiguous references/unsupported terms are not
guessed; verify the review balance. Existing deferred/retention records are not split
again. The retention view remains independent.

Data/configuration: no manual SQL or new invoice columns. Local cache is
 data/cash-plan-payment-term-cache.json
and is included in the updater's normal data backup. Optional cache-path property:
 cashflow.finance-plan.payment-terms-cache=<file path>
The existing cashflow.tally.server.url and cashflow.tally.company-name are preserved.
If multiple companies are loaded, configure the exact destination company name.
config/cash-plan-payment-terms.json is now a fallback and optional expected-date source,
not the list of invoice numbers eligible for automatic discovery. On a successful live
read, current narrations take precedence, including removal of old percentage terms.
No need to add new invoices to this file. Retain existing config when updating.

Install over existing app: stop Cashflow on its host, extract the update ZIP separately,
run Apply Cash Plan Update.cmd, start the existing shortcut, Ctrl+F5, then Sync Now and
refresh Finance Cash Plan. The updater backs up the WAR, data and config. No installed
production files or Tally entries were changed while preparing/testing this release.

## Repository configuration and publishing

The checked-in payment-term mapping is intentionally empty. Automatic narration
discovery works without manual invoice mappings. Optional Finance-specific fallback
rules and expected collection dates must be configured locally, never committed
with live customer balances. Mock test invoices are test fixtures only.

This repository contains standalone Cashflow only. It has no ERP PO-to-Tally
exporter or monthly stock report. Tally access is read-only. ZIP installers,
bundled Java, WARs, user databases, logs and backups are build/local artifacts
and are excluded from Git. Use the packaging script to generate installers.

To reset an existing administrator from source, stop the application, set
CASHFLOW_BOOTSTRAP_ADMIN_USERNAME and CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD for
this session, then run Reset-Admin-And-Start.cmd. No reset password is stored
in the script. Clear both environment variables after stopping the app.
