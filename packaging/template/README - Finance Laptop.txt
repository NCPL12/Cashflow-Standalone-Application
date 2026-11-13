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
- Outstanding tables include a separate Retention column, total and Retention
  only filter, using the same logic as the ERP cashflow version.
- Dedicated Tally ledgers/party names containing Retention or Retainage identify
  retention balances. Retention is already included within Outstanding.
- It does not calculate a retained portion inside an ordinary invoice automatically.

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
