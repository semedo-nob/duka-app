import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { StockPill } from '../components/Shared';
import { useToast } from '../components/Toast';
import type { Product } from '../lib/api';
import { useProducts, useReceiveStock, useAdjustStock, useProductMovements } from '../lib/queries';

function ReceiveStockDrawer({ products, onClose, showToast }: { products: Product[]; onClose: () => void; showToast: (m: string) => void }) {
  const [productId, setProductId] = useState(products[0]?.id);
  const [qty, setQty] = useState('');
  const [cost, setCost] = useState('');
  const [supplier, setSupplier] = useState('Kamau Wholesalers');
  const receiveStock = useReceiveStock();

  function submit() {
    const qtyNum = parseInt(qty);
    if (!productId || !qtyNum) return;
    receiveStock.mutate(
      { id: productId, qty: qtyNum, cost: cost ? parseFloat(cost) : undefined, supplier },
      {
        onSuccess: () => {
          onClose();
          showToast('Stock received');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not receive stock'),
      }
    );
  }

  return (
    <Drawer
      title="Receive stock"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={receiveStock.isPending || !qty} onClick={submit}>
            {receiveStock.isPending ? 'Receiving…' : 'Receive'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Supplier</label>
        <select value={supplier} onChange={(e) => setSupplier(e.target.value)}>
          <option>Kamau Wholesalers</option>
          <option>Nakumatt Distributors</option>
          <option>Add new supplier…</option>
        </select>
      </div>
      <div className="form-field">
        <label>Product</label>
        <select value={productId} onChange={(e) => setProductId(Number(e.target.value))}>
          {products.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row2">
        <div className="form-field">
          <label>Quantity received</label>
          <input type="number" placeholder="0" value={qty} onChange={(e) => setQty(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Cost per unit</label>
          <input type="number" placeholder="KSh" value={cost} onChange={(e) => setCost(e.target.value)} />
        </div>
      </div>
      <div style={{ padding: '12px 14px', background: 'var(--brand-soft)', borderRadius: 10, fontSize: 12.5, color: 'var(--brand-ink)' }}>
        Stock and cost will update automatically once received — no manual entry needed.
      </div>
    </Drawer>
  );
}

function AdjustStockDrawer({ products, onClose, showToast }: { products: Product[]; onClose: () => void; showToast: (m: string) => void }) {
  const [productId, setProductId] = useState(products[0]?.id);
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('Damaged');
  const adjustStock = useAdjustStock();

  function submit() {
    const deltaNum = parseInt(delta);
    if (!productId || !deltaNum) return;
    adjustStock.mutate(
      { id: productId, delta: deltaNum, reason },
      {
        onSuccess: () => {
          onClose();
          showToast('Stock adjusted');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not adjust stock'),
      }
    );
  }

  return (
    <Drawer
      title="Adjust stock"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={adjustStock.isPending || !delta} onClick={submit}>
            {adjustStock.isPending ? 'Saving…' : 'Confirm adjustment'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Product</label>
        <select value={productId} onChange={(e) => setProductId(Number(e.target.value))}>
          {products.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-field">
        <label>Adjustment</label>
        <input type="number" placeholder="e.g. -5" value={delta} onChange={(e) => setDelta(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Reason</label>
        <select value={reason} onChange={(e) => setReason(e.target.value)}>
          <option>Damaged</option>
          <option>Lost</option>
          <option>Found</option>
          <option>Counting error</option>
          <option>Opening balance</option>
        </select>
      </div>
    </Drawer>
  );
}

function StockHistoryDrawer({ product, onClose }: { product: Product; onClose: () => void }) {
  const { data: movements, isLoading } = useProductMovements(product.id);
  return (
    <Drawer title={product.name} onClose={onClose}>
      <div className="card" style={{ padding: 16, marginBottom: 18 }}>
        <div className="tot-row grand">
          <span>Current stock</span>
          <span>{product.stock}</span>
        </div>
      </div>
      <h4 style={{ fontSize: 13, margin: '0 0 10px' }}>Recent movements</h4>
      {isLoading && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>Loading…</p>}
      {movements && movements.length === 0 && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>No movements recorded yet.</p>}
      {movements?.map((m) => (
        <div className="alert-row" key={m.id}>
          <span
            className="alert-dot"
            style={{ background: m.type === 'adjusted' ? 'var(--warn)' : 'var(--ink-faint)' }}
          />
          <div className="text">
            <b>
              {m.qty > 0 ? '+' : ''}
              {m.qty} units {m.type}
            </b>
            <br />
            <span style={{ color: 'var(--ink-faint)' }}>{m.note}</span>
          </div>
        </div>
      ))}
    </Drawer>
  );
}

export default function Inventory() {
  const [drawer, setDrawer] = useState<'receive' | 'adjust' | null>(null);
  const [historyProduct, setHistoryProduct] = useState<Product | null>(null);
  const [search, setSearch] = useState('');
  const showToast = useToast();
  const { data: products, isLoading, isError } = useProducts();

  const filtered = (products || []).filter((p) => p.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <>
      <div className="toolbar">
        <div className="search-box">
          <Icons.search size={15} />
          <input
            placeholder="Search inventory…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ border: 'none', background: 'transparent', outline: 'none', width: '100%', fontSize: 13 }}
          />
        </div>
        <div className="toolbar-spacer" />
        <button className="btn btn-ghost" disabled={!products?.length} onClick={() => setDrawer('adjust')}>
          Adjust stock
        </button>
        <button className="btn btn-accent" disabled={!products?.length} onClick={() => setDrawer('receive')}>
          <Icons.truck size={15} color="#3A2405" /> Receive stock
        </button>
      </div>
      <div className="card">
        {isLoading && (
          <div className="empty">
            <p>Loading inventory…</p>
          </div>
        )}
        {isError && (
          <div className="empty">
            <h3>Couldn't reach the server</h3>
            <p>Make sure the API is running (npm run server).</p>
          </div>
        )}
        {products && filtered.length === 0 && (
          <div className="empty">
            <h3>No inventory items found</h3>
            <p>Try searching for a different product name.</p>
          </div>
        )}
        {products && filtered.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Current stock</th>
                <th>Reorder level</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <tr key={p.id} onClick={() => setHistoryProduct(p)}>
                  <td>
                    <div className="prod-cell">
                      <span className="thumb" style={{ background: p.color }}>
                        {p.emoji}
                      </span>
                      <div className="name">{p.name}</div>
                    </div>
                  </td>
                  <td>{p.stock} units</td>
                  <td>{p.reorder} units</td>
                  <td>
                    <StockPill product={p} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {drawer === 'receive' && products && <ReceiveStockDrawer products={products} onClose={() => setDrawer(null)} showToast={showToast} />}
      {drawer === 'adjust' && products && <AdjustStockDrawer products={products} onClose={() => setDrawer(null)} showToast={showToast} />}
      {historyProduct && <StockHistoryDrawer product={historyProduct} onClose={() => setHistoryProduct(null)} />}
    </>
  );
}
