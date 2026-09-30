import { useState } from 'react';
import { Metric, BreakdownRow } from '../components/Shared';
import { money } from '../lib/format';
import { ModuleLocked } from '../components/ModuleLocked';
import { useCapabilities, useReports } from '../lib/queries';

export default function Reports() {
  const [tab, setTab] = useState('Today');
  const { data: caps } = useCapabilities();
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
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => {
                const rows = [['Cashier', 'Amount'], ...(data?.byCashier || []).map((row) => [row.name, String(row.val)])];
                const blob = new Blob([rows.map((row) => row.join(',')).join('\n')], { type: 'text/csv' });
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = 'duka-cashiers.csv';
                link.click();
                URL.revokeObjectURL(url);
              }}
            >
              Export CSV
            </button>
          </div>
          {(data?.byCashier || []).map((row) => (
            <BreakdownRow key={row.name} label={row.name} val={row.val} total={data?.totalSales || row.val || 1} />
          ))}
          {(!data?.byCashier || data.byCashier.length === 0) && <p style={{ fontSize: 13 }}>No completed sales in this period.</p>}
        </div>
      ) : (
        <ModuleLocked title="Advanced reports aren't on this plan" body="Cashier breakdowns and CSV export stay locked until the subscription includes them." />
      )}
    </>
  );
}
