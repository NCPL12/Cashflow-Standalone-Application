# Neptune Cashflow Standalone — developer source

Extracted on 5 November 2026 from the source used for the working Finance installer.
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

Separate Retention amounts, total, Retention-only filter and Excel export match
the existing ERP Cashflow logic: dedicated Tally names containing Retention or
Retainage identify retained balances. These values are already included within
Outstanding; partial retention inside ordinary invoices is not inferred.

The GRN/PO/SO invoice-link pipeline is an ERP-dependent Cashflow feature. This
standalone source has no ERP operational schema or records. Its pipeline API
returns an explicit unavailable/informational empty response, without querying
missing ERP tables or making background voucher lookups. This is the one
standalone extraction adaptation in the Cashflow service; the integrated pipeline
logic remains in the class for reference. No ERP linkage is claimed from an empty
standalone database.

Shared `security`, Role/RolePermission/TimeStampEntity and TallySyncCoordinator
classes are included because Cashflow depends on them. They are account/support
code, not the operational ERP application. SalesApp scans Cashflow and security
components only. Obsolete commented sample account credentials were removed.

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
