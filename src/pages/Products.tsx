import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { StockPill } from '../components/Shared';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useProducts, useAddProduct, useCategories } from '../lib/queries';
import { api } from '../lib/api';

function AddProductDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [cost, setCost] = useState('');
  const [sku, setSku] = useState('');
  const { data: categories } = useCategories();
  const [cat, setCat] = useState('');
  const [unit, setUnit] = useState('each');
  const [taxCategory, setTaxCategory] = useState('STANDARD');
  const [reorder, setReorder] = useState('');
  const addProduct = useAddProduct();

  function submit() {
    const priceNum = parseFloat(price);
    if (!name || !priceNum) return;
    addProduct.mutate(
      { name, price: priceNum, cost: cost ? parseFloat(cost) : undefined, sku: sku || undefined, barcode: /^\d{6,}$/.test(sku) ? sku : undefined, categoryId: cat ? Number(cat) : undefined, unit, taxCategory, taxRate: taxCategory === 'ZERO' ? 0 : taxCategory === 'EXEMPT' ? 0 : 0.16, reorder: reorder ? parseInt(reorder) : undefined },
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
            <option value="">General</option>
            {(categories || []).map((c) => (
              <option key={c.id} value={c.id}>{c.parentId ? `— ${c.name}` : c.name}</option>
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
              <select value={taxCategory} onChange={(e) => setTaxCategory(e.target.value)}>
                <option value="STANDARD">Standard VAT (16%)</option>
                <option value="ZERO">Zero-rated</option>
                <option value="EXEMPT">Exempt</option>
              </select>
            </div>
            <div className="form-field">
              <label>Unit</label>
              <select value={unit} onChange={(e) => setUnit(e.target.value)}>
                <option value="each">Each</option>
                <option value="kg">Kg</option>
                <option value="litre">Litre</option>
                <option value="pack">Pack</option>
              </select>
            </div>
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

function CategoryEditor() {
  const { data: categories } = useCategories();
  const qc = useQueryClient();
  const showToast = useToast();
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');

  function refresh(message: string) {
    qc.invalidateQueries({ queryKey: ['categories'] });
    showToast(message);
  }

  return (
    <div className="card" style={{ marginBottom: 12, padding: 16 }}>
      <strong>Categories</strong>
      <p style={{ fontSize: 13, color: 'var(--ink-soft)' }}>Suggestions are a starting point. Rename them, remove them, or add your own.</p>
      <div className="form-row2">
        <input placeholder="New category" value={name} onChange={(e) => setName(e.target.value)} />
        <select value={parentId} onChange={(e) => setParentId(e.target.value)}>
          <option value="">Top level</option>
          {(categories || []).map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </select>
      </div>
      <button
        className="btn btn-ghost btn-sm"
        style={{ marginTop: 8 }}
        disabled={!name.trim()}
        onClick={() => api.categories.create({ name: name.trim(), parentId: parentId ? Number(parentId) : null }).then(() => { setName(''); refresh('Category added'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not add category'))}
      >
        Add category
      </button>
      <table style={{ marginTop: 12 }}>
        <tbody>
          {(categories || []).map((category) => (
            <tr key={category.id}>
              <td>
                {editingId === category.id ? (
                  <input
                    aria-label={`New name for ${category.name}`}
                    value={editingName}
                    autoFocus
                    onChange={(e) => setEditingName(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Escape') setEditingId(null);
                      if (e.key === 'Enter' && editingName.trim() && editingName.trim() !== category.name) {
                        api.categories.update(category.id, { name: editingName.trim(), parentId: category.parentId })
                          .then(() => { setEditingId(null); refresh('Category renamed'); })
                          .catch((err) => showToast(err instanceof Error ? err.message : 'Could not rename'));
                      }
                    }}
                  />
                ) : (
                  <>{category.parentId ? '↳ ' : ''}{category.name}</>
                )}
              </td>
              <td>
                {editingId === category.id ? (
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={!editingName.trim() || editingName.trim() === category.name}
                    onClick={() => api.categories.update(category.id, { name: editingName.trim(), parentId: category.parentId })
                      .then(() => { setEditingId(null); refresh('Category renamed'); })
                      .catch((err) => showToast(err instanceof Error ? err.message : 'Could not rename'))}
                  >Save</button>
                ) : (
                  <button className="btn btn-ghost btn-sm" onClick={() => { setEditingId(category.id); setEditingName(category.name); }}>Rename</button>
                )}
                <button className="btn btn-ghost btn-sm" onClick={() => api.categories.remove(category.id).then(() => refresh('Category removed')).catch((err) => showToast(err instanceof Error ? err.message : 'Could not remove'))}>Remove</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function Products() {
  const [showAdd, setShowAdd] = useState(false);
  const [showCategories, setShowCategories] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedCat, setSelectedCat] = useState('All');
  const showToast = useToast();
  const qc = useQueryClient();
  const { data: products, isLoading, isError } = useProducts();
  const { data: categories } = useCategories();
  const categoryNames = ['All', ...new Set((categories || []).map((c) => c.name))];

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
          {categoryNames.filter((c) => c !== 'All').map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div className="toolbar-spacer" />
        <select
          className="btn btn-ghost"
          defaultValue=""
          onChange={(e) => {
            const key = e.target.value;
            e.target.value = '';
            if (!key) return;
            api.categories.applyTemplate(key).then(() => {
              qc.invalidateQueries({ queryKey: ['categories'] });
              showToast('Suggested categories added. Rename, delete, or add your own.');
            }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not add categories'));
          }}
        >
          <option value="">Suggest categories…</option>
          <option value="grocery">Grocery</option>
          <option value="electronics">Electronics</option>
          <option value="clothing">Clothing</option>
          <option value="pharmacy">Pharmacy</option>
          <option value="hardware">Hardware</option>
          <option value="restaurant">Restaurant</option>
          <option value="cosmetics">Cosmetics</option>
          <option value="general">General retail</option>
        </select>
        <button className="btn btn-ghost" onClick={() => setShowCategories((open) => !open)}>
          {showCategories ? 'Hide categories' : 'Manage categories'}
        </button>
        <button className="btn btn-accent" onClick={() => setShowAdd(true)}>
          <Icons.plus size={15} color="#3A2405" /> Add product
        </button>
      </div>
      {showCategories && <CategoryEditor />}
      <div className="card">
        {isLoading && (
          <div className="empty">
            <p>Loading products…</p>
          </div>
        )}
        {isError && (
          <div className="empty">
            <h3>Couldn't reach the server</h3>
            <p>Check the connection and try again.</p>
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
