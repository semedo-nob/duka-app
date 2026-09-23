import type { Product } from '../lib/api';

export function Metric({
  label,
  value,
  delta,
  dir,
}: {
  label: string;
  value: string | number;
  delta?: string;
  dir?: 'up' | 'down' | '';
}) {
  return (
    <div className="card metric">
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {delta && <div className={`delta ${dir || ''}`}>{delta}</div>}
    </div>
  );
}

export function AlertRow({ kind, title, sub }: { kind: 'bad' | 'warn' | 'neutral'; title: string; sub: string }) {
  const colors: Record<string, string> = { bad: 'var(--bad)', warn: 'var(--warn)', neutral: 'var(--ink-faint)' };
  return (
    <div className="alert-row">
      <span className="alert-dot" style={{ background: colors[kind] }} />
      <div className="text">
        <b>{title}</b>
        <br />
        <span style={{ color: 'var(--ink-faint)' }}>{sub}</span>
      </div>
    </div>
  );
}

export function StockPill({ product }: { product: Product }) {
  if (product.stock === 0) return <span className="pill pill-bad">Out of stock</span>;
  if (product.stock <= product.reorder) return <span className="pill pill-warn">Low stock</span>;
  return <span className="pill pill-good">In stock</span>;
}

export function BreakdownRow({ label, val, total }: { label: string; val: number; total: number }) {
  const pct = Math.round((val / total) * 100);
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
        <span style={{ fontWeight: 700 }}>{label}</span>
        <span style={{ color: 'var(--ink-soft)' }}>{'KSh ' + val.toLocaleString('en-KE')}</span>
      </div>
      <div style={{ height: 6, background: 'var(--border-soft)', borderRadius: 4, overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${pct}%`, background: 'var(--brand)', borderRadius: 4 }} />
      </div>
    </div>
  );
}
