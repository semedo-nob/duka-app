import { useState } from 'react';
import { Metric, BreakdownRow } from '../components/Shared';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useCapabilities, useUnlockCapability, useReports } from '../lib/queries';

export default function Reports() {
  const [tab, setTab] = useState('Today');
  const { data: caps } = useCapabilities();
  const unlockCapability = useUnlockCapability();
  const { data, isLoading, isError } = useReports(tab);

  return (
    <>
      <div className="tabs">
        {['Today', 'This week', 'This month', 'Custom'].map((t) => (
          <button key={t} className={`tab${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="empty">
          <p>Loading report…</p>
        </div>
      )}
      {isError && (
        <div className="empty">
          <h3>Couldn't reach the server</h3>
          <p>Make sure the API is running (npm run server).</p>
        </div>
      )}

      {data && (
        <>
          <div className="metric-grid" style={{ gridTemplateColumns: 'repeat(4,1fr)' }}>
            <Metric label="Total sales" value={money(data.totalSales)} />
            <Metric label="Transactions" value={data.transactions} />
            <Metric label="Items sold" value={data.itemsSold} />
            <Metric label="Avg. transaction" value={money(data.avgTransaction)} />
          </div>
          <div className="dash-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <div className="card panel">
              <h3>By payment method</h3>
              {data.byMethod.length === 0 && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>No sales recorded yet.</p>}
              {data.byMethod.map((m) => (
                <BreakdownRow key={m.label} label={m.label[0].toUpperCase() + m.label.slice(1)} val={m.val} total={data.totalSales || 1} />
              ))}
            </div>
            <div className="card panel">
              <h3>Top products</h3>
              {data.topProducts.length === 0 && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>No sales recorded yet.</p>}
              {data.topProducts.map((p) => (
                <BreakdownRow key={p.name} label={p.name} val={p.val} total={data.totalSales || 1} />
              ))}
            </div>
          </div>
        </>
      )}

      {caps?.advReports ? (
        <div className="card panel" style={{ marginTop: 16 }}>
          <div className="row-head">
            <h3 style={{ margin: 0 }}>By cashier</h3>
            <button className="btn btn-ghost btn-sm">Export CSV</button>
          </div>
          <p style={{ fontSize: 12.5, color: 'var(--ink-soft)' }}>
            Per-cashier attribution needs sales to be tagged with who rang them up — wire that into the checkout flow
            once cashier login is added.
          </p>
        </div>
      ) : (
        <div className="card" style={{ marginTop: 16, padding: 16, display: 'flex', alignItems: 'center', gap: 14 }}>
          <div className="cap-ic" style={{ background: 'var(--border-soft)', color: 'var(--ink-faint)', flexShrink: 0 }}>
            <Icons.box size={17} color="var(--ink-faint)" />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 13.5 }}>Advanced reports</div>
            <div style={{ fontSize: 12.5, color: 'var(--ink-soft)' }}>Breakdowns by cashier, category and exportable CSVs.</div>
          </div>
          <button className="btn btn-ghost btn-sm" disabled={unlockCapability.isPending} onClick={() => unlockCapability.mutate('advReports')}>
            Unlock
          </button>
        </div>
      )}
    </>
  );
}
