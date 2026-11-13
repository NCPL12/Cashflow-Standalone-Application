const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../..');
const scriptDir = path.join(root, 'src/main/resources/static/cashflow/js');
const html = fs.readFileSync(path.join(root, 'src/main/resources/templates/cashflow/cashflow-forecast.html'), 'utf8')
    .replace(/<!--\/\*[\s\S]*?\*\/-->/g, '');
const tick = () => new Promise(resolve => setImmediate(resolve));

class Element {
    constructor() {
        this.value = ''; this.hidden = false; this.disabled = false; this.events = {}; this.textContent = ''; this.innerHTML = '';
        const classes = new Set();
        this.classList = {add: key => classes.add(key), remove: key => classes.delete(key),
            toggle(key) {if (classes.has(key)) {classes.delete(key); return false;} classes.add(key); return true;}};
    }
    addEventListener(type, callback) {this.events[type] = callback;}
    async emit(type, event = {}) {await this.events[type]({currentTarget: this, ...event}); await tick();}
    scrollIntoView() {}
    click() {}
    remove() {}
}

(async () => {
    for (const name of ['top-performers', 'debtors-creditors', 'strategy-summary']) {
        vm.runInNewContext(fs.readFileSync(path.join(scriptDir, name + '.js'), 'utf8'), {});
    }
    const elements = Object.fromEntries([...html.matchAll(/\bid="([^"]+)"/g)].map(match => [match[1], new Element()]));
    assert.equal(elements.pipelineViewBtn, undefined);
    assert.equal(elements.pipelineDetailBody, undefined);
    const calls = [], alerts = [], charts = [], downloads = [];
    const data = {from:'2099-01-05', to:'2099-01-11', totalReceivables:35000, totalPayables:5000,
        openingBalance:1000, closingBalance:31000, finalCumulative:30000, backlogBills:[],
        periods:[{weekStart:'2099-01-05', weekEnd:'2099-01-11', receivables:35000, payables:5000, closingBalance:31000,
            bills:[{id:'sample-1', direction:'RECEIVABLE', partyName:'Example <Customer>', invoiceNumber:'INV-1',
                dueDate:'2099-01-05', amount:35000}]}]};
    let initialize;
    const storage = new Map();
    const context = {
        console, Intl, Date, URL, URLSearchParams, Map,
        alert: message => alerts.push(message),
        setTimeout: callback => callback(),
        localStorage: {getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value)},
        document: {
            documentElement: new Element(),
            getElementById: id => elements[id] || null,
            querySelector: () => null,
            addEventListener: (type, callback) => {if (type === 'DOMContentLoaded') initialize = callback;},
            createElement: () => new Element(),
            body: {appendChild: element => downloads.push(element)}
        },
        Chart: class {constructor(_canvas, config) {charts.push(config);} destroy() {}},
        bootstrap: {Modal: {getOrCreateInstance: () => ({show() {}})}},
        async fetch(url, options = {}) {
            assert.ok(!url.includes('invoice-pipeline'), 'Disabled pipeline must never be requested');
            calls.push({url, options});
            let payload;
            if (url.includes('/projected-forecast')) payload = data;
            else if (url.endsWith('/tally/status')) payload = {config:{serverAvailable:false}};
            else if (url.endsWith('/tally/sync')) payload = {success:true};
            else if (url.endsWith('/finance-plan/settings')) payload = {openingBalance:0, balanceAsOf:'2099-01-01'};
            else if (url.includes('/finance-plan/invoice-schedule/')) payload = {success:true};
            else throw new Error('Unexpected endpoint: ' + url);
            return {ok:true, json:async () => payload, blob:async () => new Blob(['test'])};
        }
    };
    vm.runInNewContext(fs.readFileSync(path.join(scriptDir, 'cashflow-forecast.js'), 'utf8'), context);
    initialize();
    await tick();
    assert.equal(charts.length, 1);
    assert.match(elements.forecastReceivables.textContent, /35,000/);
    assert.match(elements.forecastPayables.textContent, /5,000/);
    await elements.sidebarCollapseBtn.emit('click');
    assert.equal(storage.get('financeSidebarCollapsed'), 'true');
    await elements.applyForecast.emit('click');
    await elements.resetForecast.emit('click');
    let latest = calls.filter(call => call.url.includes('/projected-forecast')).at(-1).url;
    assert.ok(!latest.includes('from='), 'Reset must use the default forecast range');
    charts.at(-1).options.onClick({}, [{index:1}]);
    await tick();
    latest = calls.filter(call => call.url.includes('/projected-forecast')).at(-1).url;
    assert.match(latest, /granularity=daily/);
    assert.equal(elements.backToWeekly.hidden, false);
    await elements.backToWeekly.emit('click');
    assert.equal(elements.backToWeekly.hidden, true);
    await elements.forecastReceivablesCard.emit('click');
    assert.equal(elements.forecastInvoicePanel.hidden, false);
    assert.match(elements.forecastInvoiceBody.innerHTML, /Example &lt;Customer&gt;/);
    const save = {dataset:{saveSchedule:'0'}, disabled:false};
    elements.forecastInvoiceBody.querySelector = () => ({value:'2099-01-06'});
    await elements.forecastInvoiceBody.emit('click', {target:{closest: selector => selector === '[data-save-schedule]' ? save : null}});
    assert.ok(calls.some(call => call.url.endsWith('/invoice-schedule/sample-1') && call.options.method === 'PUT'
        && JSON.parse(call.options.body).expectedCashDate === '2099-01-06'));
    await elements.forecastReceivablesCard.emit('click');
    await elements.exportForecastInvoices.emit('click');
    assert.equal(downloads.length, 1);
    assert.ok(calls.some(call => call.url.endsWith('/projected-forecast/export') && call.options.method === 'POST'
        && JSON.parse(call.options.body)[0] === 'sample-1'));
    elements.forecastOpeningInput.value = '2000';
    elements.forecastOpeningDate.value = '2099-01-01';
    await elements.saveForecastOpening.emit('click');
    assert.ok(calls.some(call => call.url.endsWith('/finance-plan/settings') && call.options.method === 'PUT'
        && JSON.parse(call.options.body).openingBalance === 2000));
    await elements.syncNowBtn.emit('click');
    assert.equal(elements.syncNowBtn.disabled, false);
    assert.equal(elements.saveForecastOpening.disabled, false);
    assert.deepEqual(alerts, []);
    console.log('PASS: disabled scripts are inert; forecast initialization, filters, daily detail, schedules, export, balance and sync work without pipeline elements.');
})().catch(error => {console.error(error); process.exitCode = 1;});