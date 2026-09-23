import { Icons } from '../components/Icons';
import { useCapabilities, useUnlockCapability } from '../lib/queries';
import type { CapabilityKey } from '../lib/api';

const CAPS: { key: CapabilityKey | null; name: string; desc: string }[] = [
  { key: null, name: 'Core POS', desc: 'Sell, cart, payments, receipts.' },
  { key: null, name: 'Inventory', desc: 'Stock tracking and movements.' },
  { key: null, name: 'Customers', desc: 'Customer profiles and history.' },
  { key: null, name: 'Expenses', desc: 'Track money spent.' },
  { key: 'purchasing', name: 'Purchasing', desc: 'Purchase orders and deliveries.' },
  { key: 'credit', name: 'Customer credit', desc: 'Sell on credit, track balances.' },
  { key: 'advReports', name: 'Advanced reports', desc: 'Deeper breakdowns and exports.' },
  { key: 'multiBranch', name: 'Multi-branch', desc: 'Run more than one location.' },
  { key: 'etims', name: 'eTIMS', desc: 'Automatic tax submission.' },
];

export default function Billing() {
  const { data: caps } = useCapabilities();
  const unlockCapability = useUnlockCapability();

  return (
    <>
      <p className="page-desc" style={{ marginTop: -14 }}>
        Your business is growing — add the capabilities you need, whenever you need them.
      </p>
      <div className="cap-grid">
        {CAPS.map((c) => {
          const active = c.key === null || !!caps?.[c.key];
          return (
            <div key={c.name} className="card cap-card">
              <div className="cap-top">
                <div
                  className="cap-ic"
                  style={{ background: active ? 'var(--brand-soft)' : 'var(--border-soft)', color: active ? 'var(--brand)' : 'var(--ink-faint)' }}
                >
                  <Icons.box size={17} color={active ? 'var(--brand)' : 'var(--ink-faint)'} />
                </div>
                {active && <span className="pill pill-good">Active</span>}
              </div>
              <h4>{c.name}</h4>
              <p>{c.desc}</p>
              {!active && c.key && (
                <button
                  className="btn btn-ghost btn-sm"
                  style={{ alignSelf: 'flex-start' }}
                  disabled={unlockCapability.isPending}
                  onClick={() => unlockCapability.mutate(c.key!)}
                >
                  Unlock
                </button>
              )}
            </div>
          );
        })}
      </div>
    </>
  );
}
