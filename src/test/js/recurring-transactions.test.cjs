/* Recurring transactions page disabled: not in use.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../../..');
const tick = () => new Promise(resolve => setImmediate(resolve));

async function verify(standalone) {
    const elements = {};
    class Element {
        constructor(id) { this.id = id; this.value = ''; this.textContent = ''; this.innerHTML = ''; this.style = {}; this.disabled = false; this.events = {}; this.visible = false; this.dataset = {}; this.classList = {contains: () => this.visible}; }
        addEventListener(type, callback, options) { (this.events[type] ||= []).push({callback, once: options?.once}); }
        async dispatch(type, event = {}) {
            const listeners = [...(this.events[type] || [])];
            this.events[type] = (this.events[type] || []).filter(item => !item.once);
            for (const item of listeners) await item.callback(event);
        }
        querySelector(selector) { return elements[selector.slice(1)]; }
        reset() { for (const element of Object.values(elements)) element.value = ''; }
        reportValidity() { return this.valid !== false; }
        closest() { return this; }
    }
    const ids = ['recurringTransactionsModal','recurringModal','recurringForm','recurringLoadingBar','recurringLoadingBarInner',
        'recurringStatus','recurringFormStatus','totalCount','monthlyTotal','dueSoonCount','recurringTableBody','recurringSaveButton',
        'transactionId','modalTitle','name','description','category','type','dayOfMonth','amount','nextDueDate','notes'];
    ids.forEach(id => elements[id] = new Element(id));
    let rows = [], nextId = 1, failSave = false, failLoad = false, failDelete = false;
    const calls = [];
    const context = {
        console, Intl, Date, Headers, URL, encodeURIComponent,
        document: {
            getElementById: id => elements[id],
            body: {appendChild: element => { assert.equal(element.id, 'recurringModal'); }},
            querySelector: selector => ({content: selector.includes('cashflow-base') ? '/cashflow-analyzer' : selector.includes('csrf-token') ? 'test-token' : 'X-CSRF-TOKEN'})
        },
        location: {origin: 'http://localhost', pathname: '/cashflow-analyzer/' + (standalone ? 'recurring-transactions' : 'overview')},
        confirm: () => true,
        bootstrap: {Modal: {getOrCreateInstance: element => ({
            show() { element.visible = true; element.dispatch('shown.bs.modal'); },
            hide() { element.visible = false; element.dispatch('hidden.bs.modal'); }
        })}},
        async fetch(url, options = {}) {
            const method = options.method || 'GET';
            calls.push({url, method, options});
            assert.ok(url.startsWith('/cashflow-analyzer/api/cashflow/recurring-transactions'));
            if (method !== 'GET') assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test-token');
            if ((failSave && ['POST', 'PUT'].includes(method)) || (failLoad && method === 'GET') || (failDelete && method === 'DELETE')) {
                return {ok: false, json: async () => ({success: false, error: 'Test failure'})};
            }
            if (method === 'POST') rows.push({...JSON.parse(options.body), id: nextId++, isActive: true});
            if (method === 'PUT') rows[0] = {...rows[0], ...JSON.parse(options.body)};
            if (method === 'DELETE') rows = [];
            return {ok: true, json: async () => ({success: true, transactions: rows, totalMonthly: rows.reduce((sum, row) => sum + Number(row.amount), 0)})};
        }
    };
    context.window = context;
    vm.createContext(context);
    for (const name of ['module-paths.js', 'recurring-transactions.js']) {
        vm.runInContext(fs.readFileSync(path.join(root, 'src/main/resources/static/cashflow/js', name), 'utf8'), context);
    }
    if (!standalone) context.bootstrap.Modal.getOrCreateInstance(elements.recurringTransactionsModal).show();
    await tick();
    assert.match(elements.recurringTableBody.innerHTML, /No recurring transactions/);
    context.openAddModal();
    assert.equal(elements.recurringModal.visible, true);
    assert.equal(elements.recurringTransactionsModal.visible, false);
    const fill = () => {
        for (const [key, value] of Object.entries({name:'Office rent <script>', category:'RENT', type:'MONTHLY', dayOfMonth:'1', amount:'25000', nextDueDate:'2026-12-01'})) elements[key].value = value;
    };
    fill();
    elements.recurringForm.valid = false;
    const before = calls.length;
    await context.saveRecurringTransaction();
    assert.equal(calls.length, before, 'Invalid forms must not be saved');
    elements.recurringForm.valid = true;
    failSave = true;
    await context.saveRecurringTransaction();
    assert.equal(elements.recurringModal.visible, true);
    assert.match(elements.recurringFormStatus.textContent, /Test failure/);
    assert.equal(elements.recurringSaveButton.disabled, false);
    assert.equal(elements.amount.value, '25000');
    failSave = false;
    await context.saveRecurringTransaction();
    await tick();
    assert.equal(rows.length, 1);
    assert.equal(elements.recurringTransactionsModal.visible, true);
    assert.match(elements.recurringTableBody.innerHTML, /Office rent &lt;script&gt;/);
    assert.match(elements.monthlyTotal.textContent, /25,000/);
    assert.equal(elements.totalCount.textContent, 1);
    const button = new Element('edit');
    button.dataset = {action:'edit', id:'1'};
    await elements.recurringTableBody.dispatch('click', {target:button});
    assert.equal(elements.amount.value, '25000');
    elements.amount.value = '26000';
    await context.saveRecurringTransaction();
    await tick();
    assert.equal(rows[0].amount, '26000');
    assert.ok(calls.some(call => call.method === 'PUT' && call.url.endsWith('/1')));
    failLoad = true;
    await elements.recurringTransactionsModal.dispatch('shown.bs.modal');
    assert.match(elements.recurringStatus.textContent, /Could not load/);
    assert.match(elements.recurringTableBody.innerHTML, /Office rent/);
    assert.equal(elements.recurringLoadingBar.style.display, 'none');
    failLoad = false;
    button.dataset.action = 'delete';
    context.confirm = () => false;
    await elements.recurringTableBody.dispatch('click', {target:button});
    assert.equal(rows.length, 1);
    context.confirm = () => true;
    failDelete = true;
    await elements.recurringTableBody.dispatch('click', {target:button});
    assert.match(elements.recurringStatus.textContent, /Could not delete/);
    assert.equal(button.disabled, false);
    failDelete = false;
    await elements.recurringTableBody.dispatch('click', {target:button});
    assert.equal(rows.length, 0);
    assert.equal(elements.totalCount.textContent, 0);
    assert.match(elements.recurringTableBody.innerHTML, /No recurring transactions/);
}
(async () => {
    await verify(false);
    await verify(true);
    const html = fs.readFileSync(path.join(root, 'src/main/resources/templates/cashflow/recurring-transactions.html'), 'utf8');
    assert.match(html, /<script defer th:src="@\{\/cashflow-analyzer\/js\/recurring-transactions.js\}"><\/script>\s*<\/div>/);
    assert.match(html, /bootstrap.bundle.min.js/);
    assert.match(html, /id="amount" min="0.01"/);
    console.log('PASS: standalone and overview loading, validation, create/edit/delete, CSRF, escaping and failure handling');
})().catch(error => {console.error(error); process.exitCode = 1;});
*/
