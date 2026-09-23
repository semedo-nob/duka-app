import { useState } from 'react';
import { Icons } from '../Icons';
import { money } from '../../lib/format';
import { useCreateSale, useCustomers, useCapabilities } from '../../lib/queries';
import type { CartLine } from '../../store/useStore';

type Method = 'cash' | 'mpesa' | 'card' | 'split' | 'credit';
type Step = 'method' | 'complete' | 'error';

export function PaymentOverlay({
  cart,
  total,
  onClose,
  onComplete,
  showToast,
}: {
  cart: CartLine[];
  total: number;
  onClose: () => void;
  onComplete: () => void;
  showToast: (msg: string) => void;
}) {
  const [step, setStep] = useState<Step>('method');
  const [method, setMethod] = useState<Method | null>(null);
  const [cashAmt, setCashAmt] = useState(total);
  const [splitCash, setSplitCash] = useState(0);
  const [splitMpesa, setSplitMpesa] = useState(0);
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | ''>('');
  const [saleNo, setSaleNo] = useState<number | null>(null);

  const { data: customers } = useCustomers();
  const { data: caps } = useCapabilities();
  const createSale = useCreateSale();

  const methods: { id: Method; label: string; emoji: string }[] = [
    { id: 'cash', label: 'Cash', emoji: '💵' },
    { id: 'mpesa', label: 'M-Pesa', emoji: '📱' },
    { id: 'card', label: 'Card', emoji: '💳' },
    { id: 'split', label: 'Split', emoji: '➗' },
    ...(caps?.credit ? [{ id: 'credit' as Method, label: 'Credit', emoji: '📝' }] : []),
  ];

  const canConfirm =
    method === 'credit'
      ? !!selectedCustomerId
      : method === 'cash'
      ? cashAmt >= total
      : method === 'split'
      ? splitCash + splitMpesa >= total
      : method !== null;

  function complete() {
    createSale.mutate(
      {
        items: cart.map((l) => ({ productId: l.id, name: l.name, qty: l.qty, price: l.price })),
        method: method || 'cash',
        customerId: selectedCustomerId ? Number(selectedCustomerId) : undefined,
      },
      {
        onSuccess: (sale) => {
          setSaleNo(sale.id);
          setStep('complete');
        },
        onError: (err) => {
          showToast(err instanceof Error ? err.message : 'Sale could not be recorded');
          setStep('error');
        },
      }
    );
  }

  if (step === 'complete') {
    return (
      <div className="pos-overlay">
        <div className="receipt-wrap">
          <div className="check-circle">
            <Icons.check size={30} color="var(--good)" />
          </div>
          <h2>Sale complete</h2>
          <div className="amt serif">{money(total)}</div>
          <div className="saleno">Sale #{saleNo}</div>
          <div className="receipt-actions">
            <button className="btn btn-primary btn-lg" onClick={() => showToast('Sending to printer…')}>
              Print receipt
            </button>
            <button className="btn btn-ghost" onClick={() => showToast('Receipt sent to customer')}>
              Send receipt
            </button>
            <button className="btn btn-ghost" onClick={onComplete}>
              New sale
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (step === 'error') {
    return (
      <div className="pos-overlay">
        <div className="receipt-wrap">
          <div className="check-circle" style={{ background: 'var(--bad-soft)', color: 'var(--bad)' }}>
            <Icons.alert size={26} color="var(--bad)" />
          </div>
          <h2>Payment not completed</h2>
          <p className="msg">The sale has not been finalized. Nothing has been charged or recorded.</p>
          <div className="receipt-actions">
            <button className="btn btn-primary btn-lg" onClick={() => setStep('method')}>
              Try the payment again
            </button>
            <button className="btn btn-ghost" onClick={onClose}>
              Cancel sale
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="pos-overlay">
      <div className="pay-header">
        <button className="back-btn" onClick={onClose}>
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="m15 18-6-6 6-6" />
          </svg>
        </button>
        <h2>Take payment</h2>
      </div>
      <div className="pay-body">
        <div className="pay-inner">
          <div className="pay-total-display">
            <div className="tl">AMOUNT DUE</div>
            <div className="tv">{money(total)}</div>
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-faint)', display: 'block', marginBottom: 6 }}>
              Customer (optional)
            </label>
            <select
              style={{ width: '100%', padding: '10px 12px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--surface)', fontSize: 13 }}
              value={selectedCustomerId}
              onChange={(e) => setSelectedCustomerId(e.target.value ? Number(e.target.value) : '')}
            >
              <option value="">Walk-in Customer</option>
              {(customers || []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.phone || 'No phone'})
                </option>
              ))}
            </select>
          </div>

          <div className="method-grid">
            {methods.map((m) => (
              <div
                key={m.id}
                className={`method-tile${method === m.id ? ' selected' : ''}`}
                onClick={() => setMethod(m.id)}
              >
                <span className="ic" style={{ background: 'var(--brand-soft)', fontSize: 18 }}>
                  {m.emoji}
                </span>
                {m.label}
              </div>
            ))}
          </div>

          {method === 'cash' && (
            <div className="cash-pad">
              <label className="field-label">Cash received</label>
              <input
                className="cash-input"
                type="number"
                value={cashAmt}
                onChange={(e) => setCashAmt(parseFloat(e.target.value) || 0)}
              />
              <div className="quick-cash">
                {[...new Set([total, Math.ceil(total / 50) * 50, Math.ceil(total / 100) * 100 + 100])].map((v) => (
                  <button key={v} onClick={() => setCashAmt(v)}>
                    {money(v)}
                  </button>
                ))}
              </div>
              <div className={`change-row ${cashAmt - total < 0 ? 'neg' : ''}`}>
                <span>{cashAmt - total < 0 ? 'Still owed' : 'Change due'}</span>
                <span>{money(Math.abs(cashAmt - total))}</span>
              </div>
            </div>
          )}

          {method === 'split' && (
            <div className="cash-pad">
              <div className="form-row2">
                <div>
                  <label className="field-label">Cash</label>
                  <input
                    className="cash-input"
                    style={{ fontSize: 16, padding: '10px 12px' }}
                    type="number"
                    value={splitCash || ''}
                    onChange={(e) => setSplitCash(parseFloat(e.target.value) || 0)}
                  />
                </div>
                <div>
                  <label className="field-label">M-Pesa</label>
                  <input
                    className="cash-input"
                    style={{ fontSize: 16, padding: '10px 12px' }}
                    type="number"
                    value={splitMpesa || ''}
                    onChange={(e) => setSplitMpesa(parseFloat(e.target.value) || 0)}
                  />
                </div>
              </div>
              <div className={`change-row ${splitCash + splitMpesa < total ? 'neg' : ''}`}>
                <span>{splitCash + splitMpesa < total ? 'Remaining' : 'Covered'}</span>
                <span>{money(Math.abs(total - (splitCash + splitMpesa)))}</span>
              </div>
            </div>
          )}

          {(method === 'mpesa' || method === 'card') && (
            <div style={{ textAlign: 'center', padding: '22px 0', color: 'var(--ink-soft)', fontSize: 13.5 }}>
              Ask the customer to tap, insert, or complete their {method === 'mpesa' ? 'M-Pesa' : 'card'} payment.
              {method === 'card' && (
                <div>
                  <button
                    onClick={() => setStep('error')}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--ink-faint)',
                      fontSize: 11.5,
                      textDecoration: 'underline',
                      cursor: 'pointer',
                      marginTop: 8,
                    }}
                  >
                    Simulate a declined card (demo)
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      <div className="pay-footer">
        <button className="btn btn-primary btn-lg" style={{ width: '100%' }} disabled={!canConfirm || createSale.isPending} onClick={complete}>
          {method === null ? 'Select a payment method' : createSale.isPending ? 'Processing…' : canConfirm ? 'Complete sale' : 'Enter full amount'}
        </button>
      </div>
    </div>
  );
}
