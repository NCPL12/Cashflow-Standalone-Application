/* Recurring transactions page disabled: not in use.
(function () {
    'use strict';
    const API = '/api/cashflow/recurring-transactions';
    const page = document.getElementById('recurringTransactionsModal');
    const editor = document.getElementById('recurringModal');
    const form = document.getElementById('recurringForm');
    const field = id => editor.querySelector('#' + id);
    const money = value => new Intl.NumberFormat('en-IN', {style: 'currency', currency: 'INR'}).format(value || 0);
    const escape = value => String(value == null ? '' : value).replace(/[&<>"']/g,
        ch => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[ch]));
    let transactions = [];
    let restorePage = false;

    function status(id, message) {
        document.getElementById(id).textContent = message || '';
    }

    async function request(url, options) {
        const response = await fetch(url, options);
        let data;
        try { data = await response.json(); }
        catch (_error) { throw new Error('Unable to read the server response. Please sign in again if your session expired.'); }
        if (!response.ok || data.success === false) throw new Error(data.error || 'Unable to complete the request.');
        return data;
    }

    async function loadRecurringTransactions() {
        const loading = document.getElementById('recurringLoadingBar');
        loading.style.display = 'block';
        document.getElementById('recurringLoadingBarInner').style.width = '100%';
        status('recurringStatus', '');
        try {
            const data = await request(API);
            transactions = data.transactions;
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const end = new Date(today);
            end.setDate(end.getDate() + 7);
            document.getElementById('totalCount').textContent = transactions.length;
            document.getElementById('monthlyTotal').textContent = money(data.totalMonthly);
            document.getElementById('dueSoonCount').textContent = transactions.filter(item => {
                const due = new Date(item.nextDueDate + 'T00:00:00');
                return due >= today && due <= end;
            }).length;
            const body = document.getElementById('recurringTableBody');
            body.innerHTML = transactions.length ? transactions.map(item => `<tr>
                <td>${escape(item.name)}</td><td><span class="category-badge">${escape(item.category)}</span></td>
                <td>${escape(item.type)}</td><td>${escape(item.dayOfMonth)}</td><td>${money(item.amount)}</td>
                <td>${escape(item.nextDueDate)}</td><td>${item.isActive ? 'Active' : 'Inactive'}</td>
                <td><button type="button" class="action-btn btn-edit" data-action="edit" data-id="${escape(item.id)}">Edit</button>
                <button type="button" class="action-btn btn-delete" data-action="delete" data-id="${escape(item.id)}">Delete</button></td>
                </tr>`).join('') : '<tr><td colspan="8" class="no-data-message">No recurring transactions yet. Click "Add New" to create your first entry.</td></tr>';
        } catch (error) {
            status('recurringStatus', 'Could not load recurring transactions: ' + error.message);
        } finally {
            loading.style.display = 'none';
        }
    }

    function openEditor(item) {
        form.reset();
        status('recurringFormStatus', '');
        field('transactionId').value = item ? item.id : '';
        field('modalTitle').textContent = item ? 'Edit Recurring Transaction' : 'Add Recurring Transaction';
        for (const key of ['name', 'description', 'category', 'type', 'dayOfMonth', 'amount', 'nextDueDate', 'notes']) {
            field(key).value = item ? (item[key] == null ? '' : item[key]) : '';
        }
        // Bootstrap supports one modal at a time; restore the list after the editor closes.
        restorePage = page.classList.contains('show');
        if (restorePage) {
            page.addEventListener('hidden.bs.modal', () => bootstrap.Modal.getOrCreateInstance(editor).show(), {once: true});
            bootstrap.Modal.getOrCreateInstance(page).hide();
        } else {
            bootstrap.Modal.getOrCreateInstance(editor).show();
        }
    }

    window.openAddModal = function () { openEditor(null); };
    window.saveRecurringTransaction = async function () {
        if (!form.reportValidity()) return;
        const button = document.getElementById('recurringSaveButton');
        if (button.disabled) return;
        button.disabled = true;
        status('recurringFormStatus', '');
        const id = field('transactionId').value;
        const payload = {};
        for (const key of ['name', 'description', 'category', 'type', 'nextDueDate', 'notes']) payload[key] = field(key).value.trim();
        payload.dayOfMonth = Number(field('dayOfMonth').value);
        payload.amount = field('amount').value;
        try {
            await request(API + (id ? '/' + encodeURIComponent(id) : ''), {
                method: id ? 'PUT' : 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
            });
            bootstrap.Modal.getOrCreateInstance(editor).hide();
            if (!restorePage) await loadRecurringTransactions();
        } catch (error) {
            status('recurringFormStatus', 'Could not save: ' + error.message);
        } finally {
            button.disabled = false;
        }
    };

    form.addEventListener('submit', event => { event.preventDefault(); window.saveRecurringTransaction(); });
    document.getElementById('recurringTableBody').addEventListener('click', async event => {
        const button = event.target.closest('button[data-action]');
        if (!button || button.disabled) return;
        const item = transactions.find(value => String(value.id) === button.dataset.id);
        if (!item) return;
        if (button.dataset.action === 'edit') { openEditor(item); return; }
        if (!window.confirm('Delete recurring expense "' + item.name + '"?')) return;
        button.disabled = true;
        try {
            await request(API + '/' + encodeURIComponent(item.id), {method: 'DELETE'});
            await loadRecurringTransactions();
        } catch (error) {
            status('recurringStatus', 'Could not delete: ' + error.message);
        } finally {
            button.disabled = false;
        }
    });
    // Keep the editor outside the fullscreen modal to avoid nested backdrops and focus traps.
    document.body.appendChild(editor);
    editor.addEventListener('hidden.bs.modal', () => {
        if (restorePage) bootstrap.Modal.getOrCreateInstance(page).show();
    });
    page.addEventListener('shown.bs.modal', loadRecurringTransactions);
    if (window.location.pathname.endsWith('/recurring-transactions')) {
        bootstrap.Modal.getOrCreateInstance(page).show();
    }
})();
*/
