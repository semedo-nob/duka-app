import { useEffect, useRef, useState } from 'react';
import { Icons } from '../Icons';
import { money } from '../../lib/format';
import { useCreateSale, useCustomers, useCapabilities } from '../../lib/queries';
import { api } from '../../lib/api';
import { commitLocalCheckout, ensureIdentity, markFailed, markSynced } from '../../lib/localDb';
import { cartTotals } from '../../lib/cartMath';
import { printReceipt } from '../../lib/receipt';
import type { LedgerSale } from '../../lib/posLedger';
import { useStore, type CartLine } from '../../store/useStore';

function splitPayments(cash: number, total: number) {
  const parts: { method: string; amount: number }[] = [];
  let left = total;
  if (cash > 0 && left > 0) {
    const amount = Math.min(cash, left);
    parts.push({ method: 'cash', amount });
    left -= amount;
  }
  if (left > 0) parts.push({ method: 'mpesa', amount: left });
  return parts;
}

type Method = 'cash' | 'mpesa' | 'card' | 'split' | 'credit';
type Step = 'method' | 'pending' | 'complete' | 'error';

export function PaymentOverlay({
  cart,
  total,
  discount,
  onClose,
  onComplete,
  showToast,
}: {
  cart: CartLine[];
  total: number;
  discount: number;
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
  const [localSale, setLocalSale] = useState<LedgerSale | null>(null);
  const [printDetail, setPrintDetail] = useState('');
  const [paymentRef, setPaymentRef] = useState<string | null>(null);
  const cashierName = useStore((s) => s.user?.name);
  const businessName = useStore((s) => s.setupData.businessName);
  const [mpesaMode, setMpesaMode] = useState('mock');
  const idempotencyKey = useRef(crypto.randomUUID());

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

  useEffect(() => {
    api.mpesa.status().then((status) => setMpesaMode(status.mode)).catch(() => setMpesaMode('mock'));
  }, []);

  useEffect(() => {
    if (step !== 'pending' || !saleNo) return;
    const timer = window.setInterval(() => {
      api.sales.get(saleNo).then((sale) => {
        if (sale.paymentStatus === 'COMPLETED') setStep('complete');
        if (sale.paymentStatus === 'FAILED') setStep('error');
      }).catch(() => undefined);
    }, 2000);
    return () => window.clearInterval(timer);
  }, [step, saleNo]);

  async function complete() {
    const payload = {
      items: cart.map((l) => ({ productId: l.id, name: l.name, qty: l.qty, price: l.price })),
      method: method || 'cash',
      customerId: selectedCustomerId ? Number(selectedCustomerId) : undefined,
      discount,
      tendered: method === 'cash' ? cashAmt : undefined,
      payments: method === 'split' ? splitPayments(splitCash, total) : undefined,
      idempotencyKey: idempotencyKey.current,
    };
    const { idempotencyKey: key, ...body } = payload;
    const totals = cartTotals(cart, discount || 0);
    let committed: LedgerSale;
    try {
      committed = await commitLocalCheckout({
        idempotencyKey: key,
        method: body.method,
        items: body.items,
        discount: totals.discount,
        tax: totals.tax,
        subtotal: totals.subtotal,
        total: totals.total,
        customerId: body.customerId,
        tendered: body.tendered,
        payments: body.payments,
        cashierName,
      });
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'This device could not save the sale');
      setStep('error');
      return;
    }
    setLocalSale(committed);
    if (committed.status === 'AWAITING_PAYMENT') setStep('pending');
    else setStep('complete');
    const identity = await ensureIdentity();
    createSale.mutate({
      ...payload,
      deviceId: identity.deviceId,
      installationId: identity.installationId,
      businessId: identity.businessId,
      clientSaleNo: committed.clientSaleNo,
    }, {
      onSuccess: (sale) => {
        void markSynced(key, sale.id);
        setSaleNo(sale.id);
        setPaymentRef(sale.paymentRef || null);
        setStep(sale.paymentStatus === 'PENDING' ? 'pending' : 'complete');
      },
      onError: (err) => {
        const message = err instanceof Error ? err.message : 'Sale could not reach the server';
        if (err instanceof TypeError || /failed to fetch|network/i.test(message)) {
          showToast(`Saved on this device as ${committed.clientSaleNo}. It will sync when the connection returns.`);
          return;
        }
        void markFailed(key, message);
        showToast(`${message} Receipt ${committed.clientSaleNo} stays on this device.`);
      },
    });
  }

  async function printCurrent(reprint = false) {
    if (!localSale) return;
    const result = await printReceipt({ businessName: businessName || 'Duka', sale: localSale, reprint });
    setPrintDetail(result.detail);
    showToast(result.detail);
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
          <div className="saleno">{localSale ? `Receipt ${localSale.clientSaleNo}` : 'Sale'}{saleNo ? ` · central #${saleNo}` : ' · waiting to sync'}</div>
          {printDetail && <p className="msg">{printDetail}</p>}
          <div className="receipt-actions">
            <button className="btn btn-primary btn-lg" onClick={() => void printCurrent(false)}>
              Print receipt
            </button>
            <button className="btn btn-ghost" onClick={() => void printCurrent(true)}>
              Reprint
            </button>
            <button className="btn btn-ghost" disabled title="SMS and email receipts are not connected">
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

  if (step === 'pending') {
    return (
      <div className="pos-overlay">
        <div className="receipt-wrap">
          <h2>Waiting for M-Pesa</h2>
          <div className="amt serif">{money(total)}</div>
          <p className="msg">
            {localSale ? `Saved on this device as ${localSale.clientSaleNo}. ` : ''}
            Stock has not moved. M-Pesa is unpaid until a verified confirmation. Reference {paymentRef || 'not sent yet'}.
          </p>
          {mpesaMode === 'mock' && paymentRef && (
            <button
              className="btn btn-primary btn-lg"
              onClick={() => {
                api.mpesa.sandboxConfirm(paymentRef).then((sale) => {
                  if (sale.paymentStatus === 'COMPLETED') setStep('complete');
                }).catch((err) => showToast(err instanceof Error ? err.message : 'Sandbox confirmation failed'));
              }}
            >
              Record sandbox confirmation
            </button>
          )}
          <div className="receipt-actions">
            <button className="btn btn-ghost" onClick={onClose}>Back to the sale</button>
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
