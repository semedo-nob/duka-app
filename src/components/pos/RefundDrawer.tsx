import { useState } from 'react';
import { Drawer } from '../Drawer';
import { Icons } from '../Icons';
import { money } from '../../lib/format';
import { useSales, useRefundSale } from '../../lib/queries';
import type { Sale } from '../../lib/api';

export function RefundDrawer({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [sale, setSale] = useState<Sale | null>(null);
  const [checked, setChecked] = useState<Record<number, boolean>>({});
  const [done, setDone] = useState(false);
  const { data: sales, isLoading } = useSales();
  const refundSale = useRefundSale();

  const filtered = (sales || []).filter((s) => !s.refunded && String(s.id).includes(query));

  if (done) {
    return (
      <Drawer title="Refund complete" onClose={onClose}>
        <div className="empty">
          <div className="check-circle" style={{ marginBottom: 10 }}>
            <Icons.check size={24} color="var(--good)" />
          </div>
          <h3>Refund issued</h3>
          <p>Stock, payment records and the tax submission have been adjusted automatically.</p>
        </div>
      </Drawer>
    );
  }

  if (sale) {
    const refundTotal = sale.items.reduce((sum, it, i) => (checked[i] ? sum + it.price * it.qty : sum), 0);
    const anyChecked = Object.values(checked).some(Boolean);
    return (
      <Drawer
        title={`Sale #${sale.id}`}
        onClose={onClose}
        footer={
          <>
            <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setSale(null)}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              style={{ flex: 1 }}
              disabled={!anyChecked || refundSale.isPending}
              onClick={() =>
                refundSale.mutate(
                  { id: sale.id, itemIndices: Object.keys(checked).filter((i) => checked[Number(i)]).map(Number) },
                  { onSuccess: () => setDone(true) }
                )
              }
            >
              {refundSale.isPending ? 'Refunding…' : 'Refund'}
            </button>
          </>
        }
      >
        <p style={{ fontSize: 12.5, color: 'var(--ink-faint)', margin: '-4px 0 16px' }}>Select the items being returned</p>
        {sale.items.map((it, i) => (
          <label
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 11,
              padding: '11px 0',
              borderBottom: '1px solid var(--border-soft)',
              cursor: 'pointer',
            }}
          >
            <input
              type="checkbox"
              style={{ width: 16, height: 16 }}
              checked={!!checked[i]}
              onChange={(e) => setChecked((c) => ({ ...c, [i]: e.target.checked }))}
            />
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 700, fontSize: 13 }}>{it.name}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-faint)' }}>
                Qty {it.qty} · {money(it.price)} each
              </div>
            </div>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{money(it.price * it.qty)}</div>
          </label>
        ))}
        <div className="form-field" style={{ marginTop: 18 }}>
          <label>Reason</label>
          <select>
            <option>Customer changed mind</option>
            <option>Wrong item</option>
            <option>Damaged / faulty</option>
            <option>Overcharged</option>
          </select>
        </div>
        {anyChecked && (
          <div className="change-row" style={{ marginTop: 14 }}>
            <span>Refund total</span>
            <span>{money(refundTotal)}</span>
          </div>
        )}
      </Drawer>
    );
  }

  return (
    <Drawer title="Find a sale" onClose={onClose}>
      <div className="form-field">
        <input placeholder="Search by sale number…" value={query} onChange={(e) => setQuery(e.target.value)} />
      </div>
      {isLoading && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>Loading recent sales…</p>}
      {!isLoading && filtered.length === 0 ? (
        <div className="empty">
          <p>No sale found.</p>
        </div>
      ) : (
        filtered.map((s) => (
          <div
            key={s.id}
            className="detail-list-item"
            style={{ border: '1px solid var(--border-soft)', borderRadius: 10, marginBottom: 8 }}
            onClick={() => {
              setSale(s);
              setChecked({});
            }}
          >
            <div className="n">Sale #{s.id}</div>
            <div className="s">
              {new Date(s.at).toLocaleTimeString('en-KE', { hour: 'numeric', minute: '2-digit' })} · {s.items.length} items ·{' '}
              {money(s.total)}
            </div>
          </div>
        ))
      )}
    </Drawer>
  );
}
