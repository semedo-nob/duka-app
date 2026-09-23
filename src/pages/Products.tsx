import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { StockPill } from '../components/Shared';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useProducts, useAddProduct } from '../lib/queries';

const CATEGORIES = ['Grocery', 'Dairy', 'Beverages', 'Bakery', 'Household'];

function AddProductDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [cost, setCost] = useState('');
  const [sku, setSku] = useState('');
  const [cat, setCat] = useState(CATEGORIES[0]);
  const [reorder, setReorder] = useState('');
  const addProduct = useAddProduct();

  function submit() {
    const priceNum = parseFloat(price);
    if (!name || !priceNum) return;
    addProduct.mutate(
      { name, price: priceNum, cost: cost ? parseFloat(cost) : undefined, sku: sku || undefined, cat, reorder: reorder ? parseInt(reorder) : undefined },
      {
        onSuccess: () => {
          onClose();
          showToast('Product added');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not add product'),
      }
    );
  }

  return (
    <Drawer
      title="Add product"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={addProduct.isPending || !name || !price} onClick={submit}>
            {addProduct.isPending ? 'Saving…' : 'Save product'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Product name</label>
        <input placeholder="e.g. Maize Flour 2kg" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="form-row2">
        <div className="form-field">
          <label>Selling price</label>
          <input type="number" placeholder="KSh" value={price} onChange={(e) => setPrice(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Buying price</label>
          <input type="number" placeholder="KSh" value={cost} onChange={(e) => setCost(e.target.value)} />
        </div>
      </div>
      <div className="form-row2">
        <div className="form-field">
          <label>SKU / Barcode</label>
          <input placeholder="Scan or type" value={sku} onChange={(e) => setSku(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Category</label>
          <select value={cat} onChange={(e) => setCat(e.target.value)}>
            {CATEGORIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="more-toggle" onClick={() => setMoreOpen((v) => !v)}>
        <span style={{ display: 'inline-flex', transform: moreOpen ? 'rotate(90deg)' : 'rotate(0)', transition: 'transform .15s' }}>
          <Icons.chevronRight size={13} />
        </span>
        More options
      </div>
      {moreOpen && (
        <div className="more-fields">
          <div className="form-row2">
            <div className="form-field">
              <label>Tax category</label>
              <select>
                <option>Standard VAT (16%)</option>
                <option>Zero-rated</option>
                <option>Exempt</option>
              </select>
            </div>
            <div className="form-field">
              <label>Unit</label>
              <select>
                <option>Each</option>
                <option>Kg</option>
                <option>Litre</option>
                <option>Pack</option>
              </select>
            </div>
          </div>
          <div className="form-field">
            <label>Supplier</label>
            <input placeholder="Optional" />
          </div>
          <div className="form-field">
            <label>Reorder level</label>
            <input type="number" placeholder="e.g. 15" value={reorder} onChange={(e) => setReorder(e.target.value)} />
          </div>
        </div>
      )}
    </Drawer>
  );
}

export default function Products() {
  const [showAdd, setShowAdd] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedCat, setSelectedCat] = useState('All');
  const showToast = useToast();
  const { data: products, isLoading, isError } = useProducts();

  const filtered = (products || []).filter(
    (p) =>
      (selectedCat === 'All' || p.cat === selectedCat) &&
      (p.name.toLowerCase().includes(search.toLowerCase()) || p.sku.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <>
      <div className="toolbar">
        <div className="search-box">
          <Icons.search size={15} />
          <input
            placeholder="Search products…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ border: 'none', background: 'transparent', outline: 'none', width: '100%', fontSize: 13 }}
          />
        </div>
        <select
          className="btn btn-ghost"
          style={{ fontWeight: 700, cursor: 'pointer', paddingRight: 10 }}
          value={selectedCat}
          onChange={(e) => setSelectedCat(e.target.value)}
        >
          <option value="All">All Categories</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShowAdd(true)}>
          <Icons.plus size={15} color="#3A2405" /> Add product
        </button>
      </div>
      <div className="card">
        {isLoading && (
          <div className="empty">
            <p>Loading products…</p>
          </div>
        )}
        {isError && (
          <div className="empty">
            <h3>Couldn't reach the server</h3>
            <p>Make sure the API is running (npm run server) at the URL in VITE_API_URL.</p>
          </div>
        )}
        {products && filtered.length === 0 && (
          <div className="empty">
            <h3>No products found</h3>
            <p>Try matching another search query or category.</p>
          </div>
        )}
        {products && filtered.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Price</th>
                <th>Stock</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => (
                <tr key={p.id}>
                  <td>
                    <div className="prod-cell">
                      <span className="thumb" style={{ background: p.color }}>
                        {p.emoji}
                      </span>
                      <div>
                        <div className="name">{p.name}</div>
                        <div className="sku">{p.sku}</div>
                      </div>
                    </div>
                  </td>
                  <td>{money(p.price)}</td>
                  <td>{p.stock} units</td>
                  <td>
                    <StockPill product={p} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {showAdd && <AddProductDrawer onClose={() => setShowAdd(false)} showToast={showToast} />}
    </>
  );
}
