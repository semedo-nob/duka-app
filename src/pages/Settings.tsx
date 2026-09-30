import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { useToast } from '../components/Toast';
import { useSettings, useUpdateSettings } from '../lib/queries';
import { setAuthToken } from '../lib/api';
import { useStore } from '../store/useStore';
import type { Settings as SettingsType } from '../lib/api';

type SettingsKey = 'business' | 'payments' | 'receipts' | 'tax';

const GROUPS: { key: SettingsKey | 'team' | 'billing' | 'account' | 'support' | 'diagnostics'; title: string; desc: string }[] = [
  { key: 'account', title: 'My account', desc: 'PIN, sessions, and the business account' },
  { key: 'business', title: 'Business', desc: 'Name, address, receipt logo' },
  { key: 'payments', title: 'Payments', desc: 'Cash, M-Pesa till, card terminal' },
  { key: 'receipts', title: 'Receipts', desc: 'Layout, footer message' },
  { key: 'tax', title: 'Tax / eTIMS', desc: 'Taxpayer PIN, submission log' },
  { key: 'team', title: 'Team & permissions', desc: 'Roles and access' },
  { key: 'billing', title: 'Billing', desc: 'Plan, payment, and what is unlocked' },
  { key: 'support', title: 'Support', desc: 'Tickets with Duka' },
  { key: 'diagnostics', title: 'Diagnostics', desc: 'This device, sync, and printers' },
];

const TITLES: Record<SettingsKey, string> = {
  business: 'Business',
  payments: 'Payments',
  receipts: 'Receipts',
  tax: 'Tax / eTIMS',
};

function SettingsDrawer({ kind, current, onClose }: { kind: SettingsKey; current: SettingsType; onClose: () => void }) {
  const showToast = useToast();
  const updateSettings = useUpdateSettings();
  const [form, setForm] = useState<Record<string, string>>(current[kind] as unknown as Record<string, string>);

  function set(field: string, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function save() {
    updateSettings.mutate(
      { section: kind, data: form as never },
      {
        onSuccess: () => {
          onClose();
          showToast(`${TITLES[kind]} updated`);
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not save changes'),
      }
    );
  }

  return (
    <Drawer
      title={TITLES[kind]}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={updateSettings.isPending} onClick={save}>
            {updateSettings.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </>
      }
    >
      {kind === 'business' && (
        <>
          <div className="form-field">
            <label>Business name</label>
            <input value={form.name} onChange={(e) => set('name', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Business type</label>
            <input value={form.type} onChange={(e) => set('type', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Address</label>
            <input placeholder="Street, town" value={form.address} onChange={(e) => set('address', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Phone</label>
            <input value={form.phone} onChange={(e) => set('phone', e.target.value)} />
          </div>
        </>
      )}
      {kind === 'payments' && (
        <>
          <div className="form-field">
            <label>M-Pesa till number</label>
            <input placeholder="e.g. 174379" value={form.mpesaTill} onChange={(e) => set('mpesaTill', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Card terminal</label>
            <select value={form.cardTerminal} onChange={(e) => set('cardTerminal', e.target.value)}>
              <option>Not connected</option>
              <option>Connected</option>
            </select>
          </div>
          <div className="form-field">
            <label>Default payment method</label>
            <select value={form.defaultMethod} onChange={(e) => set('defaultMethod', e.target.value)}>
              <option>Cash</option>
              <option>M-Pesa</option>
            </select>
          </div>
        </>
      )}
      {kind === 'receipts' && (
        <>
          <div className="form-field">
            <label>Footer message</label>
            <input value={form.footer} onChange={(e) => set('footer', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Show logo on receipt</label>
            <select value={form.showLogo} onChange={(e) => set('showLogo', e.target.value)}>
              <option>Yes</option>
              <option>No</option>
            </select>
          </div>
          <div className="form-field">
            <label>Receipt copies</label>
            <select value={form.copies} onChange={(e) => set('copies', e.target.value)}>
              <option>1</option>
              <option>2</option>
            </select>
          </div>
        </>
      )}
      {kind === 'tax' && (
        <>
          <div className="form-field">
            <label>Taxpayer PIN</label>
            <input placeholder="P0XXXXXXXXX" value={form.pin} onChange={(e) => set('pin', e.target.value)} />
          </div>
          <div className="form-field">
            <label>Default tax category</label>
            <select value={form.taxCategory} onChange={(e) => set('taxCategory', e.target.value)}>
              <option>Standard VAT (16%)</option>
              <option>Zero-rated</option>
              <option>Exempt</option>
            </select>
          </div>
        </>
      )}
    </Drawer>
  );
}

export default function Settings() {
  const navigate = useNavigate();
  const logOut = useStore((s) => s.logOut);
  const [drawer, setDrawer] = useState<SettingsKey | null>(null);
  const { data: settings, isLoading, isError } = useSettings();

  return (
    <>
      <div className="cap-grid" style={{ gridTemplateColumns: 'repeat(3,1fr)' }}>
        {GROUPS.map((g) => (
          <div
            key={g.key}
            className="card cap-card"
            style={{ cursor: 'pointer' }}
            onClick={() => {
              if (g.key === 'billing') navigate('/settings/billing');
              else if (g.key === 'account') navigate('/account');
              else if (g.key === 'team') navigate('/team');
              else if (g.key === 'support') navigate('/settings/support');
              else if (g.key === 'diagnostics') navigate('/settings/diagnostics');
              else setDrawer(g.key as SettingsKey);
            }}
          >
            <div className="cap-ic" style={{ background: 'var(--brand-soft)', color: 'var(--brand)' }}>
              <Icons.box size={17} color="var(--brand)" />
            </div>
            <h4>{g.title}</h4>
            <p>{g.desc}</p>
          </div>
        ))}
      </div>
      {isLoading && drawer && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>Loading…</p>}
      {isError && drawer && (
        <p style={{ fontSize: 13, color: 'var(--bad)' }}>Couldn't reach the server — make sure the API is running.</p>
      )}
      {drawer && settings && <SettingsDrawer kind={drawer} current={settings} onClose={() => setDrawer(null)} />}
      <button
        className="btn btn-ghost"
        style={{ marginTop: 18 }}
        onClick={() => {
          setAuthToken(null);
          logOut();
          navigate('/login');
        }}
      >
        Log out
      </button>
    </>
  );
}
