"""Read-only Tally verification into an isolated local Cashflow database.
Explicit --tally-url and --company are required. Never imports into Tally.
"""
import sys,os,time,json,secrets,subprocess,argparse,html,shutil
from pathlib import Path
from decimal import Decimal as D,ROUND_HALF_UP
from datetime import date
sys.path.insert(0,str(Path(__file__).parent))
from verify_package import Client,Page
parser=argparse.ArgumentParser();parser.add_argument('--tally-url',required=True);parser.add_argument('--company',required=True);parser.add_argument('--work-dir',required=True);parser.add_argument('--port',type=int,default=18087);parser.add_argument('--java',default=shutil.which('java'));args=parser.parse_args()
root=Path(__file__).resolve().parents[1];work=Path(args.work_dir).resolve();work.mkdir(parents=True,exist_ok=False)
if not args.java:parser.error('Java is required. Install a JDK or pass --java with the java executable path.')
java=Path(args.java).resolve()
env=os.environ.copy();env['CASHFLOW_BOOTSTRAP_ADMIN_USERNAME']='read_only_review';env['CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD']=secrets.token_urlsafe(24)
cmd=[str(java),'-Dfile.encoding=UTF-8','-jar',str(root/'target/neptune-cashflow.war'),'--spring.profiles.active=cashflow-standalone','--server.port='+str(args.port),'--server.address=127.0.0.1','--spring.datasource.url=jdbc:h2:file:./data/cashflow_db;DB_CLOSE_ON_EXIT=FALSE','--cashflow.tally.server.url='+args.tally_url,'--cashflow.tally.company-name='+args.company,'--cashflow.tally.sync.initial-delay-ms=3600000','--cashflow.tally.sync.interval-ms=3600000']
log=open(work/'application.log','wb');app=subprocess.Popen(cmd,cwd=work,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=0x08000000)
client=Client('http://127.0.0.1:'+str(args.port));result={'status':'FAILED','company':args.company,'productionWrites':False};checks=[]
def check(name,ok):
 if not ok:raise AssertionError(name)
 checks.append(name);print('PASS '+name,flush=True)
def get(path):
 status,_,raw=client.request('/cashflow-analyzer/api/cashflow'+path)
 assert status==200,(status,raw[:700]);return json.loads(raw,parse_float=D)
def iso(v):
 if isinstance(v,list):return '%04d-%02d-%02d'%tuple(v[:3])
 return v
try:
 for _ in range(160):
  if app.poll() is not None:raise RuntimeError('App exited; inspect application.log')
  try:
   if client.request('/cashflow-health')[0]==200:break
  except Exception:pass
  time.sleep(1)
 status,url,body=client.login(env['CASHFLOW_BOOTSTRAP_ADMIN_USERNAME'],env['CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD']);check('Isolated standalone login',status==200 and '/overview' in url)
 token=Page(body.decode()).csrf
 status,_,raw=client.request('/cashflow-analyzer/api/cashflow/tally/sync',{},token);check('Live Tally Export snapshot read into isolated H2 only',status==200 and json.loads(raw).get('success'))
 before=get('/tally/outstanding?type=receivables');payables=get('/tally/outstanding?type=payables');retained=get('/tally/outstanding?type=receivables&retentionOnly=true')
 plan=get('/finance-plan/summary?startMonth=2026-11&months=3')
 check('Finance Cash Plan read current live payment terms',plan['paymentTermsSource']=='TALLY' and not plan['paymentTermsWarning'])
 cache=json.loads((work/'data/cash-plan-payment-term-cache.json').read_text(),parse_float=D)
 rows=before['rows'];deferred=D(0);review=D(0);applied=[];reviews=[]
 original=[D(0)]*3;deductions=[D(0)]*3
 def month_index(row):
  value=iso(row.get('dueDate'))
  if not value:return None
  due=date.fromisoformat(value)
  if due<date(2026,11,1):return 0
  if due>date(2027,1,31):return None
  return (due.year-2026)*12+due.month-11
 for row in rows:
  i=month_index(row)
  if i is not None:original[i]+=D(str(row['amount']))
 for rule in cache['active']:
  matching=[b for b in rows if b['invoiceNumber'].strip().upper()==rule['invoiceNumber'].strip().upper() and b['partyName'].strip().upper()==rule['customerName'].strip().upper() and iso(b['invoiceDate'])==rule['invoiceDate']]
  if len(matching)!=1:continue
  b=matching[0];value=D(str(b['amount']));i=month_index(b)
  duplicate=any(x['invoiceNumber'].strip().upper()==b['invoiceNumber'].strip().upper() and iso(x['invoiceDate'])==iso(b['invoiceDate']) for x in retained['rows'])
  if rule.get('reviewReason') or value!=D(str(rule['expectedOutstanding'])) or duplicate:
   review+=value;reviews.append({'invoice':b['invoiceNumber'],'amount':str(value),'reason':rule.get('reviewReason') or 'Changed outstanding / separately allocated'})
   if i is not None:deductions[i]+=value
   continue
  held=(D(str(rule['basicAmount']))*(D(100)-D(str(rule['payableBasicPercent'])))/D(100)).quantize(D('.01'),rounding=ROUND_HALF_UP)
  deferred+=held
  if i is not None:deductions[i]+=held
  applied.append({'invoice':b['invoiceNumber'],'fullOutstanding':str(value),'basic':str(rule['basicAmount']),'gst':str(rule['gstAmount']),'percent':str(rule['payableBasicPercent']),'normalCashPlanAmount':str(value-held),'deferredAmount':str(held)})
 check('Deferred amount independently reconciles invoice by invoice',deferred==D(str(plan['commissioningBalance'])) and len(applied)==plan['paymentTermsAppliedCount'])
 check('Review balances are disclosed rather than guessed',review==D(str(plan['paymentTermsReviewBalance'])))
 for i,period in enumerate(plan['periods']):check('Monthly normal cash-in reconciliation '+period['month'],original[i]-deductions[i]==D(str(period['tallyCashIn'])))
 check('Undated deferred amounts are excluded from monthly collections',all(D(str(p['commissioningCashIn']))==0 for p in plan['periods']))
 after=get('/tally/outstanding?type=receivables')
 check('Other outstanding totals and rows are unchanged',before['total']==after['total'] and before['rows']==after['rows'])
 known={'BLR2026306','BLR2026361','BLR2026375','BLR2026424','BLR2026436','BLR2026467','BLR2026470','BLR2026539','BLR2026541'}
 check('All nine identified ordinary invoices are reflected',known.issubset({x['invoice'] for x in applied}))
 check('Separately allocated BLR2026232 is not split again','BLR2026232' not in {x['invoice'] for x in applied})
 result.update(status='PASSED',checks=checks,applied=applied,reviews=reviews,deferredTotal=str(deferred),monthly=[{'month':p['month'],'original':str(original[i]),'deduction':str(deductions[i]),'cashPlan':str(p['tallyCashIn'])} for i,p in enumerate(plan['periods'])],checkedVouchers=plan['paymentTermsCheckedVouchers'])
 print(json.dumps({'appliedCount':len(applied),'deferredTotal':str(deferred),'reviewCount':len(reviews),'monthly':result['monthly']},indent=2),flush=True)
except Exception as e:
 result.update(error=str(e),checks=checks);print('FAIL '+str(e),flush=True);raise
finally:
 (work/'verification-result.json').write_text(json.dumps(result,indent=2,default=str),encoding='utf-8')
 if app.poll() is None:
  app.terminate()
  try:app.wait(20)
  except subprocess.TimeoutExpired:app.kill();app.wait()
 log.close()
