import { useState } from 'react';
import { Drawer } from '../Drawer';
import { Icons } from '../Icons';
import { money } from '../../lib/format';
import { useStore } from '../../store/useStore';
import { useDashboard } from '../../lib/queries';
import { api } from '../../lib/api';
import { ensureIdentity } from '../../lib/localDb';

export function ShiftDrawer({ onClose }: { onClose: () => void }) {
  const shift = useStore((s) => s.shift);
  const closeShift = useStore((s) => s.closeShift);
  const openShift = useStore((s) => s.openShift);
  const [opening, setOpening] = useState('');
  const [error, setError] = useState('');
  const [step, setStep] = useState<'summary' | 'close' | 'done'>('summary');
  const [declared, setDeclared] = useState<number | ''>('');
  const { data } = useDashboard();

  const cashSales = data?.cash ?? 0;
  const mpesaSales = data?.mpesa ?? 0;
  const cardSales = data?.card ?? 0;
  const refunds = data?.refundedTotal ?? 0;
  const expected = shift.openingCash + cashSales - refunds;

  if (!shift.open && step !== 'done') {
    return (
      <Drawer title="Shift summary" onClose={onClose}>
        <div className="empty">
          <h3>No shift open</h3>
          <p>Sales still work. Opening a shift records the cash float on the server.</p>
          <div className="form-field">
            <label>Opening cash</label>
            <input type="number" value={opening} onChange={(e) => setOpening(e.target.value)} />
          </div>
          {error && <p style={{ color: 'var(--bad)', fontSize: 13 }}>{error}</p>}
          <button className="btn btn-primary" onClick={() => {
            const amount = parseFloat(opening) || 0;
            ensureIdentity().then((identity) => api.shifts.open({ openingCash: amount, deviceId: identity.deviceId })).then(() => {
              openShift(amount);
              onClose();
            }).catch((err) => setError(err instanceof Error ? err.message : 'Could not open the shift'));
          }}>Open shift</button>
        </div>
      </Drawer>
    );
  }

  if (step === 'done') {
    return (
      <Drawer title="Shift closed" onClose={onClose}>
        <div className="empty">
          <div className="check-circle" style={{ marginBottom: 10 }}>
            <Icons.check size={24} color="var(--good)" />
          </div>
          <h3>Shift closed</h3>
          <p>The counted cash was saved on the server.</p>
        </div>
      </Drawer>
    );
  }

  if (step === 'close') {
    const variance = typeof declared === 'number' ? declared - expected : null;
    return (
      <Drawer
        title="Close shift"
        onClose={onClose}
        footer={
          <>
            <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setStep('summary')}>
              Back
            </button>
            <button
              className="btn btn-primary"
              style={{ flex: 1 }}
              disabled={declared === ''}
              onClick={() => {
                ensureIdentity().then((identity) => api.shifts.close({ declaredCash: Number(declared), expectedCash: expected, deviceId: identity.deviceId })).then(() => {
                  closeShift();
                  setStep('done');
                }).catch((err) => setError(err instanceof Error ? err.message : 'Could not close the shift'));
              }}
            >
              Confirm &amp; close shift
            </button>
          </>
        }
      >
        <div className="form-field">
          <label>Expected cash</label>
          <input value={money(expected)} disabled style={{ background: 'var(--surface-alt)', color: 'var(--ink-soft)' }} />
        </div>
        <div className="form-field">
          <label>Actual cash counted</label>
          <input
            type="number"
            placeholder="Count the drawer and enter here"
            value={declared}
            onChange={(e) => setDeclared(e.target.value === '' ? '' : parseFloat(e.target.value))}
          />
        </div>
        {variance !== null && (
          <div className={`change-row ${variance < 0 ? 'neg' : ''}`} style={{ marginTop: 6 }}>
            <span>Variance</span>
            <span>{variance === 0 ? 'None' : (variance > 0 ? '+' : '−') + money(Math.abs(variance))}</span>
          </div>
        )}
      </Drawer>
    );
  }

  return (
    <Drawer
      title="Shift summary"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Keep working
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={() => setStep('close')}>
            Close shift
          </button>
        </>
      }
    >
      <p style={{ fontSize: 12.5, color: 'var(--ink-faint)', margin: '-4px 0 18px' }}>
        Opened today at {shift.opened} · opening float {money(shift.openingCash)}
      </p>
      <div className="tot-row">
        <span>Cash sales</span>
        <span>{money(cashSales)}</span>
      </div>
      <div className="tot-row">
        <span>M-Pesa sales</span>
        <span>{money(mpesaSales)}</span>
      </div>
      <div className="tot-row">
        <span>Card sales</span>
        <span>{money(cardSales)}</span>
      </div>
      <div className="tot-row">
        <span>Refunds</span>
        <span>−{money(refunds)}</span>
      </div>
      <div className="tot-row grand">
        <span>Expected cash in drawer</span>
        <span>{money(expected)}</span>
      </div>
    </Drawer>
  );
}
