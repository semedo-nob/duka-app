import { useNavigate } from 'react-router-dom';
import { Metric, AlertRow } from '../components/Shared';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useDashboard } from '../lib/queries';

function TrendChart({ vals }: { vals: number[] }) {
  const max = Math.max(...vals, 1);
  const days = ['Th', 'Fr', 'Sa', 'Su', 'Mo', 'Tu', 'We'];
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 120, paddingTop: 20 }}>
      {vals.map((v, i) => {
        const h = 12 + (v / max) * 84;
        return (
          <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, flex: 1 }}>
            <div
              style={{
                width: '100%',
                maxWidth: 34,
                height: h,
                borderRadius: 6,
                background: i === vals.length - 1 ? 'var(--accent)' : 'var(--brand-soft)',
              }}
            />
            <span style={{ fontSize: 11, color: 'var(--ink-faint)', fontWeight: 700 }}>{days[i] ?? ''}</span>
          </div>
        );
      })}
    </div>
  );
}

function QuickAction({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  const navigate = useNavigate();
  return (
    <button className="quick-action" onClick={() => navigate(to)}>
      <span className="ic">{icon}</span>
      <span>{label}</span>
    </button>
  );
}

const KIND_MAP: Record<string, 'bad' | 'warn' | 'neutral'> = { bad: 'bad', warn: 'warn', neutral: 'neutral' };

export default function Dashboard() {
  const { data, isLoading, isError } = useDashboard();

  if (isLoading) {
    return (
      <div className="empty">
        <p>Loading dashboard…</p>
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="empty">
        <h3>Couldn't reach the server</h3>
        <p>Make sure the API is running (npm run server).</p>
      </div>
    );
  }

  return (
    <>
      <div className="metric-grid">
        <Metric label="Today's sales" value={money(data.totalSales)} />
        <Metric label="Transactions" value={data.transactions} />
        <Metric label="Gross profit" value={money(data.grossProfit)} />
        <Metric label="Cash drawer" value={money(data.cash)} />
        <Metric label="M-Pesa today" value={money(data.mpesa)} />
        <Metric
          label="Low stock"
          value={`${data.lowStockCount} item${data.lowStockCount === 1 ? '' : 's'}`}
          delta={data.lowStockCount > 0 ? 'needs reorder' : ''}
          dir="down"
        />
      </div>

      <div className="dash-grid">
        <div className="card trend-wrap">
          <div className="row-head">
            <h3 style={{ margin: 0, fontSize: 14.5, fontWeight: 800 }}>Sales, last 7 days</h3>
            <span style={{ fontSize: 12, color: 'var(--ink-faint)', fontWeight: 600 }}>Today {money(data.totalSales)}</span>
          </div>
          <TrendChart vals={data.trend} />
        </div>
        <div className="card panel">
          <h3>Needs your attention</h3>
          {data.alerts.length === 0 && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>All clear — nothing needs attention.</p>}
          {data.alerts.map((a, i) => (
            <AlertRow key={i} kind={KIND_MAP[a.kind] || 'neutral'} title={a.title} sub={a.sub} />
          ))}
        </div>
      </div>

      <div className="quick-grid">
        <QuickAction to="/sell" icon={<Icons.plus size={18} color="var(--brand)" />} label="New sale" />
        <QuickAction to="/products" icon={<Icons.box size={18} color="var(--brand)" />} label="Add product" />
        <QuickAction to="/inventory" icon={<Icons.truck size={18} color="var(--brand)" />} label="Receive stock" />
        <QuickAction to="/expenses" icon={<Icons.wallet size={18} color="var(--brand)" />} label="Record expense" />
      </div>
    </>
  );
}
