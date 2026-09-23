import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useExpenses, useAddExpense } from '../lib/queries';

const CATEGORIES = ['Rent', 'Electricity', 'Transport', 'Packaging', 'Repairs', 'Staff'];

function RecordExpenseDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0]);
  const [method, setMethod] = useState('Cash');
  const [description, setDescription] = useState('');
  const addExpense = useAddExpense();

  function submit() {
    const amt = parseFloat(amount);
    if (!amt) return;
    addExpense.mutate(
      { amount: amt, category, method, description },
      {
        onSuccess: () => {
          onClose();
          showToast('Expense recorded');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not record expense'),
      }
    );
  }

  return (
    <Drawer
      title="Record expense"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={addExpense.isPending || !amount} onClick={submit}>
            {addExpense.isPending ? 'Saving…' : 'Save expense'}
          </button>
        </>
      }
    >
      <div className="form-row2">
        <div className="form-field">
          <label>Amount</label>
          <input type="number" placeholder="KSh" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Category</label>
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-field">
        <label>Payment method</label>
        <select value={method} onChange={(e) => setMethod(e.target.value)}>
          <option>Cash</option>
          <option>M-Pesa</option>
          <option>Card</option>
        </select>
      </div>
      <div className="form-field">
        <label>Description</label>
        <input placeholder="Optional note" value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
    </Drawer>
  );
}

function relativeDay(iso: string) {
  const d = new Date(iso);
  const days = Math.floor((Date.now() - d.getTime()) / 86400000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return d.toLocaleDateString('en-KE', { weekday: 'short' });
}

export default function Expenses() {
  const [show, setShow] = useState(false);
  const showToast = useToast();
  const { data: expenses, isLoading, isError } = useExpenses();

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShow(true)}>
          <Icons.plus size={15} color="#3A2405" /> Record expense
        </button>
      </div>
      <div className="card">
        {isLoading && (
          <div className="empty">
            <p>Loading expenses…</p>
          </div>
        )}
        {isError && (
          <div className="empty">
            <h3>Couldn't reach the server</h3>
            <p>Make sure the API is running (npm run server).</p>
          </div>
        )}
        {expenses && expenses.length === 0 && (
          <div className="empty">
            <h3>No expenses yet</h3>
            <p>Record your first expense to start tracking spend.</p>
          </div>
        )}
        {expenses && expenses.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Category</th>
                <th>Description</th>
                <th>Method</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {expenses.map((e) => (
                <tr key={e.id}>
                  <td>{relativeDay(e.at)}</td>
                  <td>
                    <span className="pill pill-neutral">{e.category}</span>
                  </td>
                  <td>{e.description || '—'}</td>
                  <td>{e.method}</td>
                  <td>{money(e.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {show && <RecordExpenseDrawer onClose={() => setShow(false)} showToast={showToast} />}
    </>
  );
}
