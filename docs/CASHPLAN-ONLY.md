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
