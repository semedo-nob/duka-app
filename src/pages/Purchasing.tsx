import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { ModuleLocked } from '../components/ModuleLocked';
import { useCapabilities, usePurchaseOrders, useCreatePurchaseOrder, useSuppliers, useCreateSupplier, useUpdateSupplier, useProducts } from '../lib/queries';
import { api, type Supplier } from '../lib/api';

function SupplierHistory({ id }: { id: number }) {
  const { data: products } = useProducts();
  const showToast = useToast();
  const [detail, setDetail] = useState<Awaited<ReturnType<typeof api.suppliers.get>> | null>(null);
  const [productId, setProductId] = useState('');
  const [supplierSku, setSupplierSku] = useState('');
  const [alias, setAlias] = useState('');
  const [unitCost, setUnitCost] = useState('');

  function load() {
    api.suppliers.get(id).then(setDetail).catch(() => setDetail(null));
  }

  useEffect(() => {
    load();
  }, [id]);

  return (
    <div className="card" style={{ marginTop: 12, padding: 16 }}>
      <strong>Supplier products and history</strong>
      {!detail && <p style={{ fontSize: 13 }}>Loading…</p>}
      {detail && (
        <>
          <div className="form-row2" style={{ marginTop: 12 }}>
            <div className="form-field">
              <label>Link a product</label>
              <select value={productId} onChange={(e) => setProductId(e.target.value)}>
                <option value="">Select a product</option>
                {(products || []).map((product) => (
                  <option key={product.id} value={product.id}>{product.name}</option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label>Supplier SKU</label>
              <input value={supplierSku} onChange={(e) => setSupplierSku(e.target.value)} />
            </div>
          </div>
          <div className="form-row2">
            <div className="form-field">
              <label>Alias on their invoice</label>
              <input value={alias} onChange={(e) => setAlias(e.target.value)} />
            </div>
            <div className="form-field">
              <label>Their price</label>
              <input type="number" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
            </div>
          </div>
          <button
            className="btn btn-ghost btn-sm"
            disabled={!productId}
            onClick={() => api.suppliers.offer(id, { productId: Number(productId), supplierSku, alias, unitCost: unitCost ? parseFloat(unitCost) : undefined }).then(() => { setProductId(''); setSupplierSku(''); setAlias(''); setUnitCost(''); load(); showToast('Product linked to supplier'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not link product'))}
          >
            Save supplier product
          </button>
          <table style={{ marginTop: 12 }}>
            <thead>
              <tr><th>Product</th><th>Supplier SKU</th><th>Alias</th><th>Their price</th></tr>
            </thead>
            <tbody>
              {detail.offers.length === 0 && <tr><td colSpan={4}>No products linked yet.</td></tr>}
              {detail.offers.map((offer) => (
                <tr key={offer.id}>
                  <td>{offer.productName}</td>
                  <td>{offer.supplierSku || '—'}</td>
                  <td>{offer.alias || '—'}</td>
                  <td>{offer.unitCost == null ? '—' : money(offer.unitCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <table style={{ marginTop: 12 }}>
            <thead>
              <tr><th>Record</th><th>Reference</th><th>Status</th></tr>
            </thead>
            <tbody>
              {detail.history.length === 0 && <tr><td colSpan={3}>No purchase orders or receipts yet.</td></tr>}
              {detail.history.map((row, index) => (
                <tr key={`${row.kind}-${row.reference}-${index}`}>
                  <td>{row.kind}</td>
                  <td>{row.reference || '—'}</td>
                  <td>{row.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}

function CreatePODrawer({ onClose }: { onClose: () => void }) {
  const { data: products } = useProducts();
  const { data: suppliers } = useSuppliers();
  const createPO = useCreatePurchaseOrder();
  const showToast = useToast();
  const [supplierId, setSupplierId] = useState('');
  const [productId, setProductId] = useState('');
  const [qty, setQty] = useState('1');
  const [cost, setCost] = useState('');

  function submit() {
    const chosen = (suppliers || []).find((s) => String(s.id) === supplierId);
    createPO.mutate(
      {
        supplierId: chosen?.id,
        supplier: chosen?.name,
        lines: productId ? [{ productId: Number(productId), qty: parseInt(qty) || 1, unitCost: cost ? parseFloat(cost) : undefined }] : [],
      },
      {
        onSuccess: () => {
          onClose();
          showToast('Purchase order created');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not create PO'),
      }
    );
  }

  return (
    <Drawer
      title="Create purchase order"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            style={{ flex: 1 }}
            disabled={createPO.isPending || !supplierId || !productId}
            onClick={submit}
          >
            {createPO.isPending ? 'Creating…' : 'Create order'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Supplier</label>
        <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
          <option value="">Select a supplier</option>
          {(suppliers || []).filter((s) => s.active !== false).map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </div>
      <div className="form-field">
        <label>Product</label>
        <select value={productId} onChange={(e) => setProductId(e.target.value)}>
          <option value="">Select a product</option>
          {(products || []).map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>
      <div className="form-row2">
        <div className="form-field">
          <label>Quantity</label>
          <input type="number" value={qty} onChange={(e) => setQty(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Expected total cost</label>
          <input type="number" placeholder="KSh" value={cost} onChange={(e) => setCost(e.target.value)} />
        </div>
      </div>
    </Drawer>
  );
}

function AddSupplierDrawer({ onClose, existing }: { onClose: () => void; existing?: Supplier | null }) {
  const [name, setName] = useState(existing?.name || '');
  const [phone, setPhone] = useState(existing?.phone || existing?.contact || '');
  const [email, setEmail] = useState(existing?.email || '');
  const [address, setAddress] = useState(existing?.address || '');
  const [category, setCategory] = useState(existing?.category || '');
  const [notes, setNotes] = useState(existing?.notes || '');
  const [balance, setBalance] = useState(existing?.balance ? String(existing.balance) : '');
  const createSupplier = useCreateSupplier();
  const updateSupplier = useUpdateSupplier();
  const showToast = useToast();
  const pending = createSupplier.isPending || updateSupplier.isPending;

  function submit() {
    if (!name) return;
    const payload = { name, phone, contact: phone, email, address, category, notes, balance: balance ? parseFloat(balance) : 0, products: '' };
    const done = {
      onSuccess: () => {
        onClose();
        showToast(existing ? 'Supplier updated' : 'Supplier added');
      },
      onError: (err: unknown) => showToast(err instanceof Error ? err.message : 'Could not save supplier'),
    };
    if (existing) updateSupplier.mutate({ id: existing.id, ...payload }, done);
    else createSupplier.mutate(payload, done);
  }

  return (
    <Drawer
      title={existing ? 'Edit supplier' : 'Add supplier'}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={pending || !name} onClick={submit}>
            {pending ? 'Saving…' : 'Save supplier'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Supplier name</label>
        <input placeholder="e.g. Unga Limited" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Contact phone / email</label>
        <input placeholder="07XX XXX XXX" value={phone} onChange={(e) => setPhone(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Email</label>
        <input value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Address</label>
        <input value={address} onChange={(e) => setAddress(e.target.value)} />
      </div>
      <div className="form-field">
        <label>What they supply</label>
        <input placeholder="e.g. Flour, dairy" value={category} onChange={(e) => setCategory(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Recorded payable</label>
        <input type="number" value={balance} onChange={(e) => setBalance(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Notes</label>
        <input value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>
    </Drawer>
  );
}

export default function Purchasing() {
  const { data: caps } = useCapabilities();
  const { data: purchaseOrders, isLoading } = usePurchaseOrders();
  const [query, setQuery] = useState('');
  const { data: suppliers, isLoading: suppliersLoading } = useSuppliers(query);
  const updateSupplier = useUpdateSupplier();
  const showToast = useToast();
  const qc = useQueryClient();
  const [showCreatePO, setShowCreatePO] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [showAddSupplier, setShowAddSupplier] = useState(false);
  const [detail, setDetail] = useState<string>('');
  const [tab, setTab] = useState<'orders' | 'suppliers'>('orders');

  return (
    <>
      <div className="tabs">
        <button type="button" className={`tab${tab === 'orders' ? ' active' : ''}`} onClick={() => setTab('orders')}>
          Purchase orders
        </button>
        <button type="button" className={`tab${tab === 'suppliers' ? ' active' : ''}`} onClick={() => setTab('suppliers')}>
          Suppliers
        </button>
      </div>

      {tab === 'orders' && !caps?.purchasing ? (
        <ModuleLocked
          title="Purchase orders aren't on this plan"
          body="You can still keep a supplier directory. Creating purchase orders and receiving supplier stock stay locked until a verified subscription includes purchasing."
        />
      ) : tab === 'orders' ? (
        <>
          <div className="toolbar">
            <div className="toolbar-spacer" />
            <button className="btn btn-accent" onClick={() => setShowCreatePO(true)}>
              <Icons.plus size={15} color="#3A2405" /> Create purchase order
            </button>
          </div>
          <div className="card">
            {isLoading && (
              <div className="empty">
                <p>Loading purchase orders…</p>
              </div>
            )}
            {purchaseOrders && (
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Supplier</th>
                    <th>Items</th>
                    <th>Total</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {purchaseOrders.map((po) => (
                    <tr key={po.id}>
                      <td style={{ fontWeight: 700 }}>{po.id}</td>
                      <td>{po.supplier}</td>
                      <td>{po.items} products</td>
                      <td>{money(po.total)}</td>
                      <td>
                        {po.status === 'Delivered' ? (
                          <span className="pill pill-good">Delivered</span>
                        ) : (
                          <button className="btn btn-ghost btn-sm" onClick={() => api.purchaseOrders.receive(po.id).then(() => { qc.invalidateQueries({ queryKey: ['purchaseOrders'] }); qc.invalidateQueries({ queryKey: ['products'] }); showToast('Stock received'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not receive'))}>
                            Receive stock
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      ) : (
        <>
          <div className="toolbar">
            <input placeholder="Search suppliers" value={query} onChange={(e) => setQuery(e.target.value)} />
            <div className="toolbar-spacer" />
            <button className="btn btn-accent" onClick={() => { setEditing(null); setShowAddSupplier(true); }}>
              <Icons.plus size={15} color="#3A2405" /> Add supplier
            </button>
          </div>
          <div className="card">
            {suppliersLoading && <p style={{ padding: 16, fontSize: 13, color: 'var(--ink-faint)' }}>Loading suppliers…</p>}
            {suppliers && (
              <table>
                <thead>
                  <tr>
                    <th>Supplier</th>
                    <th>Phone</th>
                    <th>Category</th>
                    <th>Recorded payable</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {suppliers.map((s) => (
                    <tr key={s.id}>
                      <td style={{ fontWeight: 700 }}>{s.name}{s.active === false ? ' (inactive)' : ''}</td>
                      <td>{s.phone || s.contact || '—'}</td>
                      <td>{s.category || '—'}</td>
                      <td>{money(s.balance || 0)}</td>
                      <td>
                        <button className="btn btn-ghost btn-sm" onClick={() => { setEditing(s); setShowAddSupplier(true); }}>Edit</button>
                        <button className="btn btn-ghost btn-sm" onClick={() => setDetail(detail === String(s.id) ? '' : String(s.id))}>History</button>
                        <button className="btn btn-ghost btn-sm" onClick={() => updateSupplier.mutate({ ...s, name: s.name, active: s.active === false }, { onSuccess: () => showToast(s.active === false ? 'Supplier activated' : 'Supplier deactivated') })}>
                          {s.active === false ? 'Activate' : 'Deactivate'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {detail && <SupplierHistory id={Number(detail)} />}
      {showCreatePO && <CreatePODrawer onClose={() => setShowCreatePO(false)} />}
      {showAddSupplier && <AddSupplierDrawer existing={editing} onClose={() => setShowAddSupplier(false)} />}
    </>
  );
}
