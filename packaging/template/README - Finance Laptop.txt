NEPTUNE CASHFLOW FINANCE - SHARED PC INSTALLATION
================================================

1. INSTALL ON THE HOST PC
- Extract this entire ZIP. Move the Neptune Cashflow Finance folder to
  C:\Neptune Cashflow Finance (or another permanent writable folder).
- Do not run inside the ZIP. No separate Java, Maven, MySQL or Eclipse is needed.
- Open TallyPrime on the host, load the required company and enable its HTTP/XML
  server on port 9000.
- Double-click Install On This Laptop.cmd. Create the first administrator name
  and password (at least 8 characters). Setup creates a Desktop shortcut and
  the local database. Login opens after the administrator has been created.
- On subsequent days use the Neptune Cashflow Desktop shortcut.

2. TALLY CONNECTION
- Open config\application-cashflow-standalone.properties using Notepad.
- cashflow.tally.server.url=http://127.0.0.1:9000 uses Tally on this host PC.
  If Tally runs on a different PC, replace 127.0.0.1 with its reachable LAN IP.
- cashflow.tally.company-name= can be blank to use the current Tally company,
  or contain the exact company name to select a specific company.
- Automatic read refresh is every 60 seconds; Sync Now also reads Tally.
- Cashflow reads reports. It does not import POs, create vouchers or write to Tally.
- Restart Cashflow after changing configuration. If Tally is closed/unreachable,
  the app stays usable and keeps the last successful snapshot.

3. CREATE USERS AND SHARE THE APP
- Sign in as administrator. Open Users in Cashflow.
- Create a Finance user for each colleague; they sign in individually.
- Administrator users can create/disable accounts and reset passwords.
- Double-click Enable Network Access.cmd on the host and approve Windows UAC
  once. It allows TCP 8081 from the local subnet on a Private or Domain network.
- Find the host's IPv4 address with ipconfig. From another office PC open:
  http://HOST-IP:8081/login
  Example: http://192.168.0.174:8081/login (only if that is this host's IP).
- Other users need only a browser. Keep the host PC awake and Cashflow running.
- Tally configuration belongs to the host; users do not configure it separately.
- This package does not create a Windows service. Start it after host restarts.

4. RETENTION
- Overview receivables/payables/net tiles include retention.
- Normal detailed tables, aging, party concentration and Excel exports exclude
  retention. Use Retention only for its separate table and Excel.
- Normal cashflow and Finance cash-plan exclude retention even if scheduled.
- Cash Flow -> Retention only provides a separate release forecast and Excel;
  expected release dates are required to include retained bills in that forecast.
- Records are selected by membership under Current Assets > Sundry Debtors > Retention Amounts, including child groups. Unallocated ledger balances are clearly labelled with no invented dates.

5. DATA / BACKUP / UPDATES
- The embedded database is in data\cashflow_db.mv.db on the host only.
- Stop Cashflow before copying the complete data folder for a backup.
- Keep config and data when updating an existing installation.
- Reset Administrator Login.cmd changes an administrator login locally and
  preserves Cashflow data and the other user accounts.
- Logs are in logs\application.log. Stop Neptune Cashflow.cmd stops the app.
- Standalone holds Tally cashflow and Finance plans. Complete ERP GRN/PO/SO
  invoice pipeline linkage requires the ERP operational database.

6. CONNECTION TROUBLESHOOTING
- Host login: http://127.0.0.1:8081/login
- If host works but another PC cannot connect, check Private/Domain network and firewall,
  host IP, host awake, port 8081, and that both PCs can reach each other.
- If Tally is unavailable, check the configured host and port 9000, open company
  and Tally HTTP/XML server. Browser access to Cashflow and Tally are separate.

AFTER UPDATING: use Sync Now successfully to refresh stored group classification.

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
