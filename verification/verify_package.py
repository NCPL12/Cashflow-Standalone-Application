"""Exercise a standalone package using a fresh local database and a fake Tally.

No production endpoint is contacted. Run with Python 3, no third-party modules.
"""
import argparse
import html
from http.cookiejar import CookieJar
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


class Page(HTMLParser):
    def __init__(self, body):
        super().__init__()
        self.csrf = None
        self.users = {}
        self.feed(body)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "meta" and a.get("name") == "csrf-token":
            self.csrf = a.get("content") or self.csrf
        if tag == "input" and a.get("name") == "_csrf":
            self.csrf = a.get("value") or self.csrf
        if tag == "tr" and a.get("data-username"):
            self.users[a["data-username"]] = a.get("data-user-id")


class Client:
    def __init__(self, base):
        self.base = base
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(CookieJar()))

    def request(self, path, data=None, csrf=None):
        headers = {}
        if data is not None:
            data = dict(data)
            if csrf:
                data["_csrf"] = csrf
            data = urllib.parse.urlencode(data).encode()
        req = urllib.request.Request(self.base + path, data=data, headers=headers)
        try:
            response = self.opener.open(req, timeout=90)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            return response.status, response.geturl(), body

    def login(self, username, password):
        status, _, body = self.request("/login")
        assert status == 200, f"Login page status: {status}"
        token = Page(body.decode()).csrf
        assert token, "Login page has no CSRF token"
        return self.request("/login", {"username": username, "password": password}, token)


class MockTally(BaseHTTPRequestHandler):
    requests = []
    violations = []
    fail_bills = False
    cashplan_example = False
    auto_terms = False
    auto_terms_percent = 85
    fail_terms = False

    def log_message(self, *_):
        pass

    def respond(self, xml):
        body = xml.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/xml; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.respond("<RESPONSE>TallyPrime Server is running</RESPONSE>")

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        try:
            root = ET.fromstring(raw)
            action = root.findtext("./HEADER/TALLYREQUEST", "")
            request_id = root.findtext("./HEADER/ID", "")
            MockTally.requests.append({"action": action, "id": request_id})
            if action.lower() != "export":
                MockTally.violations.append(action)
                self.send_error(400, "Only read-only exports are permitted")
                return
            if request_id == "CashflowAdvanceGroups":
                data = '<GROUP NAME="Retention Amounts"><PARENT>Sundry Debtors</PARENT></GROUP><GROUP NAME="Sundry Debtors"><PARENT>Current Assets</PARENT></GROUP><GROUP NAME="Sundry Creditors"><PARENT>Current Liabilities</PARENT></GROUP>'
            elif request_id == "CashflowAdvanceLedgers":
                data = '<LEDGER NAME="Example Customer"><PARENT>Sundry Debtors</PARENT><CLOSINGBALANCE>-1000</CLOSINGBALANCE></LEDGER><LEDGER NAME="Retention - Example Project"><PARENT>Retention Amounts</PARENT><CLOSINGBALANCE>-250</CLOSINGBALANCE></LEDGER><LEDGER NAME="Example Vendor"><PARENT>Sundry Creditors</PARENT><CLOSINGBALANCE>600</CLOSINGBALANCE></LEDGER>'
            elif request_id == "CashflowOutstandingBills":
                if MockTally.fail_bills:
                    self.send_error(503, "Mock Tally temporarily unavailable")
                    return
                data = ''.join(f'<BILL NAME="{reference}"><PARENT>{party}</PARENT><BILLDATE>20261001</BILLDATE><BILLCREDITPERIOD>30 Days</BILLCREDITPERIOD><ISADVANCE>No</ISADVANCE><CLOSINGBALANCE>{amount}</CLOSINGBALANCE></BILL>' for reference, party, amount in [('DEMO-INVOICE-1', 'Example Customer', -1000), ('DEMO-RETENTION-1', 'Retention - Example Project', -250), ('DEMO-SUPPLIER-1', 'Example Vendor', 600)])
            elif request_id == "CashplanCompanies":
                data = '<COMPANY NAME="Mock Company"><NAME>Mock Company</NAME></COMPANY>'
            elif request_id in ("CashplanTermsHeaders", "CashplanTermsAmounts"):
                if MockTally.fail_terms:
                    self.send_error(503,"Mock payment terms unavailable")
                    return
                definitions=[('1','DEMO-INVOICE-1','Example Customer','20261001',1000,0,'Payment on delivery',1)]
                if MockTally.cashplan_example:definitions.append(('361','BLR2026361','KALPATARU PROJECTS INTERNATIONAL LTD - WB','20260921',1740640,313315.20,'85% Payment Along With 100% GST Value as per PO Terms.85% On Basic + GST Full amount',1))
                if MockTally.auto_terms:
                    for identity,number,basic in [('201','AUTO-A',1000),('202','AUTO-B',2000),('203','AUTO-C',500)]:
                        pct=MockTally.auto_terms_percent if number=='AUTO-A' else 85
                        narration=(str(pct)+'% Basic + Full GST') if pct else 'Payment on delivery'
                        definitions.append((identity,number,'Example Customer','20261001',basic,basic*.18,narration,1 if pct==85 else 2))
                data=''
                for identity,number,party,stamp,basic,tax,narration,alter in definitions:
                    if request_id=='CashplanTermsAmounts' and ('$MasterID = '+identity) not in raw.decode():continue
                    total=2053955 if number=='BLR2026361' else basic+tax
                    ledgers=(f'<ALLLEDGERENTRIES.LIST><LEDGERNAME>{party}</LEDGERNAME><AMOUNT>-{total}</AMOUNT></ALLLEDGERENTRIES.LIST><ALLLEDGERENTRIES.LIST><LEDGERNAME>Supply Inter State 18%</LEDGERNAME><AMOUNT>{basic}</AMOUNT></ALLLEDGERENTRIES.LIST><ALLLEDGERENTRIES.LIST><LEDGERNAME>Output IGST @18%</LEDGERNAME><AMOUNT>{tax}</AMOUNT></ALLLEDGERENTRIES.LIST>') if request_id=='CashplanTermsAmounts' else ''
                    if number=='BLR2026361' and ledgers:ledgers+='<ALLLEDGERENTRIES.LIST><LEDGERNAME>Rounded Off</LEDGERNAME><AMOUNT>-0.20</AMOUNT></ALLLEDGERENTRIES.LIST>'
                    data+=f'<VOUCHER><MASTERID>{identity}</MASTERID><ALTERID>{alter}</ALTERID><DATE>{stamp}</DATE><VOUCHERTYPENAME>Sales</VOUCHERTYPENAME><VOUCHERNUMBER>{number}</VOUCHERNUMBER><PARTYLEDGERNAME>{party}</PARTYLEDGERNAME><NARRATION>{narration}</NARRATION><ISCANCELLED>No</ISCANCELLED><ISOPTIONAL>No</ISOPTIONAL>{ledgers}</VOUCHER>'
            else:
                data = ''
            if MockTally.auto_terms and request_id == 'CashflowOutstandingBills':
                for number,amount in [('AUTO-A',1180),('AUTO-B',2360),('AUTO-C',590)]:
                    data+=f'<BILL NAME="{number}"><PARENT>Example Customer</PARENT><BILLDATE>20261001</BILLDATE><BILLCREDITPERIOD>30 Days</BILLCREDITPERIOD><CLOSINGBALANCE>-{amount}</CLOSINGBALANCE></BILL>'
            if MockTally.cashplan_example and request_id == "CashflowAdvanceLedgers":
                data += '<LEDGER NAME="KALPATARU PROJECTS INTERNATIONAL LTD - WB"><PARENT>Sundry Debtors</PARENT><CLOSINGBALANCE>-2053955.00</CLOSINGBALANCE></LEDGER>'
            if MockTally.cashplan_example and request_id == "CashflowOutstandingBills":
                data += '<BILL NAME="BLR2026361"><PARENT>KALPATARU PROJECTS INTERNATIONAL LTD - WB</PARENT><BILLDATE>20260921</BILLDATE><BILLCREDITPERIOD>45 Days</BILLCREDITPERIOD><ISADVANCE>No</ISADVANCE><CLOSINGBALANCE>-2053955.00</CLOSINGBALANCE></BILL>'
            self.respond('<ENVELOPE><STATUS>1</STATUS><BODY><DATA><COLLECTION>' + data + '</COLLECTION></DATA></BODY></ENVELOPE>')
        except Exception as error:
            MockTally.violations.append(type(error).__name__)
            self.send_error(400, "Invalid mock request")


def free_port(port):
    with socket.socket() as sock:
        try:
            sock.bind(('127.0.0.1', port))
        except OSError as error:
            raise RuntimeError(f'Test port {port} is already occupied; no process was stopped') from error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--package', required=True, type=Path)
    parser.add_argument('--test-dir', required=True, type=Path)
    parser.add_argument('--port', type=int, default=18081)
    parser.add_argument('--tally-port', type=int, default=19000)
    args = parser.parse_args()
    package = args.package.resolve()
    test_dir = args.test_dir.resolve()
    if (test_dir / 'data' / 'cashflow_db.mv.db').exists():
        raise RuntimeError('Use a new empty --test-dir so the fresh installation check is meaningful')
    test_dir.mkdir(parents=True, exist_ok=True)
    (test_dir / 'data').mkdir(exist_ok=True)
    config_filename = 'application-cashflow-standalone.properties'
    config = (package / 'config' / config_filename).read_text(encoding='utf-8-sig')
    config_replacements = {
        'cashflow.tally.server.url': f'http://127.0.0.1:{args.tally_port}',
        'cashflow.tally.sync.initial-delay-ms': '3600000',
        'cashflow.tally.sync.interval-ms': '3600000',
    }
    for key, value in config_replacements.items():
        config, count = re.subn(r'^' + re.escape(key) + r'=.*$', key + '=' + value, config, flags=re.MULTILINE)
        if count != 1:
            raise RuntimeError(f'Packaged configuration must contain exactly one {key} property')
    (test_dir / 'config').mkdir(exist_ok=True)
    (test_dir / 'config' / config_filename).write_text(config, encoding='utf-8')
    free_port(args.port)
    free_port(args.tally_port)
    checks = []

    def check(name, condition=True):
        if not condition:
            raise AssertionError(name)
        checks.append(name)
        print('PASS ' + name, flush=True)

    tally = ThreadingHTTPServer(('127.0.0.1', args.tally_port), MockTally)
    threading.Thread(target=tally.serve_forever, daemon=True).start()
    admin_name, finance_name = 'smoke_admin', 'smoke_finance'
    admin_password, finance_password = secrets.token_urlsafe(20), secrets.token_urlsafe(20)
    java = package / 'runtime' / 'bin' / 'java.exe'
    war = package / 'app' / 'neptune-cashflow.war'
    cmd = [str(java), '-Dfile.encoding=UTF-8', '-jar', str(war), '--spring.profiles.active=cashflow-standalone',
           '--spring.config.additional-location=./config/', f'--server.port={args.port}',
           '--spring.datasource.url=jdbc:h2:file:./data/cashflow_db;DB_CLOSE_ON_EXIT=FALSE']
    env = os.environ.copy()
    env['CASHFLOW_BOOTSTRAP_ADMIN_USERNAME'] = admin_name
    env['CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD'] = admin_password
    base = f'http://127.0.0.1:{args.port}'
    server = None
    log = open(test_dir / 'application.log', 'wb')
    result = {'status': 'FAILED', 'checks': checks, 'realTallyContacted': False}

    def launch(environment):
        child = subprocess.Popen(cmd, cwd=test_dir, env=environment, stdout=log, stderr=subprocess.STDOUT)
        client = Client(base)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if child.poll() is not None:
                raise RuntimeError(f'Application exited ({child.returncode}); see application.log')
            try:
                status, _, _ = client.request('/cashflow-health')
                if status == 200:
                    status, _, body = client.request('/login')
                    if status == 200:
                        return child, client, body.decode()
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(1)
        child.terminate()
        child.wait(timeout=20)
        raise RuntimeError('Application did not become ready in 180 seconds')

    def stop(child):
        if child and child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=30)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=10)

    try:
        server, public, login_html = launch(env)
        check('Fresh standalone startup uses Cashflow login', 'Neptune Cash Flow Analyzer' in login_html and 'ERP Portal' not in login_html)
        lan_addresses = []
        try:
            lan_addresses = sorted({entry[4][0] for entry in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
                                    if not entry[4][0].startswith(('127.', '169.254.')) and entry[4][0] != '0.0.0.0'})
        except socket.gaierror:
            pass
        if lan_addresses:
            lan_client = Client(f'http://{lan_addresses[0]}:{args.port}')
            status, _, _ = lan_client.request('/cashflow-health')
            check('Application responds on host LAN interface', status == 200)
            result['lanInterfaceChecked'] = lan_addresses[0]
        else:
            result['lanInterfaceCheck'] = 'Skipped: no non-loopback IPv4 address was discoverable'
            print('NOTE No LAN IPv4 adapter was discoverable; remote-PC firewall check is still required', flush=True)
        for path in ['/cashflow-analyzer/js/module-paths.js', '/cashflow-analyzer/js/auth.js', '/cashflow-analyzer/css/app-theme.css', '/cashflow-analyzer/images/neptune-logo.ico']:
            status, final_url, data = public.request(path)
            check('Public asset ' + path, status == 200 and not final_url.endswith('/login') and len(data) > 30)
        status, final_url, body = public.request('/cashflow-analyzer/overview')
        check('Anonymous overview requires login', '/login' in final_url)
        admin = Client(base)
        status, final_url, body = admin.login(admin_name, admin_password)
        check('Fresh administrator login opens Cashflow overview', status == 200 and '/cashflow-analyzer/overview' in final_url and 'Whitelabel Error' not in body.decode())
        overview = body.decode()
        check('Retention column and filter are present', 'Retention' in overview and 'retention' in overview)
        status, _, body = admin.request('/cashflow-analyzer/users')
        check('Administrator opens standalone user management', status == 200 and 'ERP Portal' not in body.decode())
        token = Page(body.decode()).csrf
        check('User management supplies CSRF token', bool(token))
        status, _, _ = admin.request('/cashflow-analyzer/users', {'username': finance_name, 'name': 'Smoke Finance', 'password': finance_password, 'role': 'CASHFLOW_USER'})
        check('User creation without CSRF is rejected', status == 403)
        status, _, body = admin.request('/cashflow-analyzer/users', {'username': finance_name, 'name': 'Smoke Finance', 'password': finance_password, 'role': 'CASHFLOW_USER'}, token)
        page = Page(body.decode())
        check('Administrator creates enabled finance user', status == 200 and finance_name in page.users)
        user_id = page.users[finance_name]
        finance = Client(base)
        status, final_url, body = finance.login(finance_name, finance_password)
        check('Finance user login opens Cashflow overview', status == 200 and '/cashflow-analyzer/overview' in final_url)
        token = Page(body.decode()).csrf
        status, _, _ = finance.request('/cashflow-analyzer/users')
        check('Finance user cannot access user management', status == 403)
        status, _, _ = finance.request('/api/users')
        check('Finance user cannot read legacy user API', status == 403)
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/status')
        status_response = json.loads(body)
        config_status = status_response.get('config', {})
        result['tallyConfigurationObserved'] = {'status': status, 'response': status_response}
        config_matches = status == 200 and config_status.get('serverUrl') == f'http://127.0.0.1:{args.tally_port}' and config_status.get('enabled') is True
        if not config_matches:
            raise AssertionError('Packaged external Tally configuration was not used: HTTP ' + str(status) + ' ' + body.decode(errors='replace')[:2000])
        check('Tally endpoint and enabled state load from packaged external config')
        status, _, sync_body = finance.request('/cashflow-analyzer/api/cashflow/tally/sync', {}, token)
        sync = json.loads(sync_body)
        check('Read-only Tally sync completes through packaged API', status == 200 and sync.get('success') is True)
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        data = json.loads(body)
        rows = data.get('rows', [])
        ordinary = [r for r in rows if r.get('invoiceNumber') == 'DEMO-INVOICE-1']
        check('Normal receivables exclude retention while overview metadata includes it', status == 200 and data.get('count') == 1 and data.get('total') == 1000 and data.get('allTotal') == 1250)
        check('Normal table contains no retention rows', len(ordinary) == 1 and not any(r.get('retention') for r in rows))
        status, _, retained_body = finance.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables&retentionOnly=true')
        retained_data = json.loads(retained_body)
        check('Separate retention view totals 250 and contains only retention', status == 200 and retained_data.get('total') == 250 and retained_data.get('count') == 1 and all(r.get('retention') for r in retained_data.get('rows', [])))
        status, _, weekly_body = finance.request('/cashflow-analyzer/api/cashflow/tally/weekly-cashflow-forecast')
        weekly = json.loads(weekly_body)
        check('Ordinary cashflow excludes retention', status == 200 and weekly.get('totalReceivables') == 1000)
        status, _, forecast_body = finance.request('/cashflow-analyzer/api/cashflow/tally/projected-forecast?retentionOnly=false')
        forecast = json.loads(forecast_body)
        check('Normal projected backlog excludes retention', status == 200 and forecast.get('backlogReceivables') == 1000)
        status, _, forecast_body = finance.request('/cashflow-analyzer/api/cashflow/tally/projected-forecast?retentionOnly=true')
        forecast = json.loads(forecast_body)
        check('Separate retention projected backlog contains only retention', status == 200 and forecast.get('backlogReceivables') == 250)
        status, _, plan_body = finance.request('/cashflow-analyzer/api/cashflow/finance-plan/summary')
        plan = json.loads(plan_body)
        check('Monthly normal totals exclude retention but expose separate retention balance', status == 200 and sum(p['tallyCashIn'] for p in plan['periods']) == 1000 and plan['retentionReceivablesBalance'] == 250)
        status, _, ret_plan_body = finance.request('/cashflow-analyzer/api/cashflow/finance-plan/summary?retentionOnly=true')
        ret_plan = json.loads(ret_plan_body)
        check('Undated monthly retention is separate and not assumed into a month', status == 200 and sum(p['totalCashIn'] for p in ret_plan['periods']) == 0 and ret_plan['retentionAwaitingDateReceivables'] == 250)
        from datetime import date, timedelta
        def json_put(path, payload):
            req=urllib.request.Request(base+path, data=json.dumps(payload).encode(), method='PUT', headers={'Content-Type':'application/json','X-CSRF-TOKEN':token})
            with finance.opener.open(req,timeout=30) as response: return response.status
        retained_id=retained_data['rows'][0]['id']
        check('Expected retention release date saved in isolated app', json_put('/cashflow-analyzer/api/cashflow/finance-plan/invoice-schedule/'+str(retained_id), {'expectedCashDate':str(date.today()+timedelta(days=1)),'remarks':'Monthly retention test'}) == 200)
        current_month=date.today().strftime('%Y-%m')
        check('Normal manual amounts saved in isolated app',json_put('/cashflow-analyzer/api/cashflow/finance-plan/manual-values',{'months':[{'month':current_month,'manualCashIn':42.50,'manualCashOut':12.25}]})==200)
        status, _, normal_body = finance.request('/cashflow-analyzer/api/cashflow/finance-plan/summary')
        normal_plan=json.loads(normal_body)
        status, _, scheduled_body = finance.request('/cashflow-analyzer/api/cashflow/finance-plan/summary?retentionOnly=true')
        scheduled=json.loads(scheduled_body)
        check('Monthly normal manual adjustments remain separate from retention',sum(p['totalCashIn'] for p in normal_plan['periods'])==1042.50 and sum(p['totalCashOut'] for p in normal_plan['periods'])==612.25 and sum(p['totalCashIn'] for p in scheduled['periods'])==250 and sum(p['manualCashIn'] for p in scheduled['periods'])==0)
        status, _, statement_html = finance.request('/cashflow-analyzer/finance-cash-plan')
        check('Monthly statement renders both controls and separate balance section',status==200 and b'data-plan-view="retention"' in statement_html and b'id="retentionReceivableBalance"' in statement_html and b'id="statementTitle"' in statement_html)
        status, _, page_body = finance.request('/cashflow-analyzer/cashflow-forecast')
        check('Cashflow page has a separate retention view', status == 200 and b'forecastRetentionOnly' in page_body)
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=payables')
        data = json.loads(body)
        check('Payables sync and classification match mock Tally', status == 200 and data.get('count') == 1 and data.get('total') == 600)
        MockTally.fail_bills = True
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/sync', {}, token)
        check('Unavailable Tally is reported as a sync failure', status >= 400 and not json.loads(body).get('success'))
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        check('Last successful balances survive Tally failure', status == 200 and json.loads(body).get('total') == 1000)
        MockTally.fail_bills = False
        status, _, body = finance.request('/cashflow-analyzer/api/cashflow/tally/outstanding/export?type=receivables&retentionOnly=true')
        check('Retention-only Excel export is generated', status == 200 and body[:2] == b'PK')
        status, _, body = admin.request('/cashflow-analyzer/users')
        token = Page(body.decode()).csrf
        status, _, body = admin.request(f'/cashflow-analyzer/users/{user_id}/enabled', {'enabled': 'false'}, token)
        disabled = Client(base)
        status, final_url, _ = disabled.login(finance_name, finance_password)
        check('Disabled finance user cannot log in', '/login' in final_url)
        check('Mock observed only Export requests, no writes to Tally', bool(MockTally.requests) and not MockTally.violations and all(r['action'].lower() == 'export' for r in MockTally.requests))
        stop(server)
        server = None
        no_bootstrap = env.copy()
        no_bootstrap.pop('CASHFLOW_BOOTSTRAP_ADMIN_USERNAME', None)
        no_bootstrap.pop('CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD', None)
        server, _, _ = launch(no_bootstrap)
        persisted = Client(base)
        status, final_url, _ = persisted.login(admin_name, admin_password)
        check('Administrator credentials persist after restart without setup credentials', status == 200 and '/cashflow-analyzer/overview' in final_url)
        status, _, body = persisted.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        check('Synced balances persist after restart', status == 200 and json.loads(body).get('total') == 1000)
        stop(server)
        server = None
        reset_password = secrets.token_urlsafe(20)
        reset_env = env.copy()
        reset_env['CASHFLOW_RESET_ADMIN'] = 'true'
        reset_env['CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD'] = reset_password
        server, _, _ = launch(reset_env)
        reset_client = Client(base)
        status, final_url, _ = reset_client.login(admin_name, reset_password)
        check('Administrator reset accepts the new password', status == 200 and '/cashflow-analyzer/overview' in final_url)
        old_credentials = Client(base)
        _, final_url, _ = old_credentials.login(admin_name, admin_password)
        check('Administrator reset rejects the old password', '/login' in final_url)
        status, _, body = reset_client.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        check('Administrator reset preserves synced balances', status == 200 and json.loads(body).get('total') == 1000)
        status, _, body = reset_client.request('/cashflow-analyzer/users')
        check('Administrator reset preserves other user accounts', status == 200 and finance_name in Page(body.decode()).users)
        MockTally.cashplan_example=True
        status, _, page_body=reset_client.request('/cashflow-analyzer/overview')
        approved_token=Page(page_body.decode()).csrf
        status, _, sync_body=reset_client.request('/cashflow-analyzer/api/cashflow/tally/sync',{},approved_token)
        check('Approved cash plan example is synced without writing to Tally',status==200 and json.loads(sync_body).get('success'))
        status, _, ordinary_body=reset_client.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        ordinary=json.loads(ordinary_body)
        check('Other outstanding screens keep the full invoice amount',ordinary['total']==2054955 and ordinary['allTotal']==2055205 and next(row['amount'] for row in ordinary['rows'] if row['invoiceNumber']=='BLR2026361')==2053955)
        status, _, plan_body=reset_client.request('/cashflow-analyzer/api/cashflow/finance-plan/summary')
        approved=json.loads(plan_body)
        check('Finance Cash Plan automatically reflects approved normal collection',sum(row['tallyCashIn'] for row in approved['periods'])==1793859 and sum(row['totalCashIn'] for row in approved['periods'])==1793901.5 and approved['paymentTermsAppliedCount']==1)
        check('Undated commissioning amount stays outside monthly totals',approved['commissioningBalance']==261096 and approved['commissioningAwaitingDate']==261096 and sum(row['commissioningCashIn'] for row in approved['periods'])==0)
        status, _, weekly_body=reset_client.request('/cashflow-analyzer/api/cashflow/tally/weekly-cashflow-forecast')
        check('Other cashflow forecast remains unchanged',json.loads(weekly_body)['totalReceivables']==2054955)
        status, _, retention_body=reset_client.request('/cashflow-analyzer/api/cashflow/finance-plan/summary?retentionOnly=true')
        check('Cash plan retention remains independently scheduled',sum(row['totalCashIn'] for row in json.loads(retention_body)['periods'])==250 and json.loads(retention_body)['paymentTermsAppliedCount']==0)
        status, _, cashplan_html=reset_client.request('/cashflow-analyzer/finance-cash-plan')
        check('No extra manual milestone mapping panel is introduced',b'paymentMilestonePanel' not in cashplan_html and b'findMilestones' not in cashplan_html)
        MockTally.auto_terms=True
        status, _, body=reset_client.request('/cashflow-analyzer/api/cashflow/tally/sync',{},approved_token)
        check('Three new invoices synced for automatic term detection',status==200 and json.loads(body).get('success'))
        def read_plan():
            status, _, body=reset_client.request('/cashflow-analyzer/api/cashflow/finance-plan/summary')
            assert status==200,body[:500]
            return json.loads(body)
        automatic=read_plan()
        check('Three unconfigured invoice references are automatically split from Tally',automatic['paymentTermsSource']=='TALLY' and automatic['paymentTermsAppliedCount']==4 and automatic['commissioningBalance']==261621 and sum(row['tallyCashIn'] for row in automatic['periods'])==1797464)
        MockTally.auto_terms_percent=90
        edited=read_plan()
        check('Edited narration updates cash plan without balance changes or manual mapping',edited['commissioningBalance']==261571 and sum(row['tallyCashIn'] for row in edited['periods'])==1797514)
        MockTally.auto_terms_percent=0
        cleared=read_plan()
        check('Removed percentage terms stop applying the old cached split',cleared['paymentTermsAppliedCount']==3 and cleared['commissioningBalance']==261471)
        MockTally.fail_terms=True
        cached=read_plan()
        check('Unavailable term lookup visibly warns and preserves last successful calculation',cached['paymentTermsSource']=='CACHED' and cached['paymentTermsWarning'] and cached['commissioningBalance']==cleared['commissioningBalance'])
        MockTally.fail_terms=False
        check('Automatic term lookup recovers when Tally returns',read_plan()['paymentTermsSource']=='TALLY')
        status, _, body=reset_client.request('/cashflow-analyzer/api/cashflow/tally/outstanding?type=receivables')
        check('Automatic terms remain confined to Finance Cash Plan',json.loads(body)['total']==2059085)
        check('Automatic metadata and bounded details are Export-only',not MockTally.violations and all(x['action'].lower()=='export' for x in MockTally.requests))
        result['status'] = 'PASSED'
    except Exception as error:
        result['error'] = str(error)
        print('FAIL ' + str(error), flush=True)
        raise
    finally:
        stop(server)
        tally.shutdown()
        tally.server_close()
        log.close()
        result['mockTallyRequests'] = MockTally.requests
        result['mockTallyViolations'] = MockTally.violations
        (test_dir / 'verification-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
