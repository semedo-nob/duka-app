import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useCapabilities, useUnlockCapability, usePurchaseOrders, useCreatePurchaseOrder, useSuppliers, useCreateSupplier, useProducts } from '../lib/queries';

function CreatePODrawer({ onClose }: { onClose: () => void }) {
  const { data: products } = useProducts();
  const { data: suppliers } = useSuppliers();
  const createPO = useCreatePurchaseOrder();
  const showToast = useToast();
  const [supplier, setSupplier] = useState('Kamau Wholesalers');
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [itemsCount, setItemsCount] = useState('3');
  const [cost, setCost] = useState('');

  function submit() {
    const totalAmt = parseFloat(cost) || 0;
    const count = parseInt(itemsCount) || selectedProducts.length || 1;
    createPO.mutate(
      { supplier, items: count, total: totalAmt },
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
            disabled={createPO.isPending || !supplier}
            onClick={submit}
          >
            {createPO.isPending ? 'Creating…' : 'Create order'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Supplier</label>
        <select value={supplier} onChange={(e) => setSupplier(e.target.value)}>
          {(suppliers || [{ name: 'Kamau Wholesalers' }, { name: 'Nakumatt Distributors' }]).map((s) => (
            <option key={s.name}>{s.name}</option>
          ))}
        </select>
      </div>
      <div className="form-field">
        <label>Products</label>
        <select
          multiple
          size={4}
          style={{ height: 'auto' }}
          value={selectedProducts}
          onChange={(e) => {
            const opts = Array.from(e.target.selectedOptions, (o) => o.value);
            setSelectedProducts(opts);
            setItemsCount(String(opts.length || 1));
          }}
        >
          {(products || []).slice(0, 10).map((p) => (
            <option key={p.id} value={p.name}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row2">
        <div className="form-field">
          <label>Number of items</label>
          <input type="number" placeholder="e.g. 3" value={itemsCount} onChange={(e) => setItemsCount(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Expected total cost</label>
          <input type="number" placeholder="KSh" value={cost} onChange={(e) => setCost(e.target.value)} />
        </div>
      </div>
    </Drawer>
  );
}

function AddSupplierDrawer({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');
  const [productsList, setProductsList] = useState('');
  const createSupplier = useCreateSupplier();
  const showToast = useToast();

  function submit() {
    if (!name) return;
    createSupplier.mutate(
      { name, contact, products: productsList },
      {
        onSuccess: () => {
          onClose();
          showToast('Supplier added');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not add supplier'),
      }
    );
  }

  return (
    <Drawer
      title="Add supplier"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={createSupplier.isPending || !name} onClick={submit}>
            {createSupplier.isPending ? 'Saving…' : 'Save supplier'}
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
        <input placeholder="07XX XXX XXX" value={contact} onChange={(e) => setContact(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Products supplied</label>
        <input placeholder="e.g. Flour, Maize" value={productsList} onChange={(e) => setProductsList(e.target.value)} />
      </div>
    </Drawer>
  );
}

export default function Purchasing() {
  const { data: caps } = useCapabilities();
  const unlockCapability = useUnlockCapability();
  const { data: purchaseOrders, isLoading } = usePurchaseOrders();
  const { data: suppliers, isLoading: suppliersLoading } = useSuppliers();
  const [showCreatePO, setShowCreatePO] = useState(false);
  const [showAddSupplier, setShowAddSupplier] = useState(false);
  const [tab, setTab] = useState<'orders' | 'suppliers'>('orders');

  if (!caps?.purchasing) {
    return (
      <div className="empty" style={{ maxWidth: 420, margin: '40px auto' }}>
        <Icons.lock size={46} color="var(--ink-faint)" />
        <h3>Purchasing isn't unlocked yet</h3>
        <p>Create purchase orders, track deliveries and manage supplier payables in one place.</p>
        <button className="btn btn-accent" disabled={unlockCapability.isPending} onClick={() => unlockCapability.mutate('purchasing')}>
          {unlockCapability.isPending ? 'Unlocking…' : 'Unlock Purchasing'}
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="tabs">
        <div className={`tab${tab === 'orders' ? ' active' : ''}`} onClick={() => setTab('orders')}>
          Purchase orders
        </div>
        <div className={`tab${tab === 'suppliers' ? ' active' : ''}`} onClick={() => setTab('suppliers')}>
          Suppliers
        </div>
      </div>

      {tab === 'orders' ? (
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
                          <span className="pill pill-warn">Awaiting delivery</span>
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
            <div className="toolbar-spacer" />
            <button className="btn btn-accent" onClick={() => setShowAddSupplier(true)}>
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
                    <th>Contact</th>
                    <th>Products supplied</th>
                  </tr>
                </thead>
                <tbody>
                  {suppliers.map((s) => (
                    <tr key={s.id || s.name}>
                      <td style={{ fontWeight: 700 }}>{s.name}</td>
                      <td>{s.contact || '—'}</td>
                      <td>{s.products || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {showCreatePO && <CreatePODrawer onClose={() => setShowCreatePO(false)} />}
      {showAddSupplier && <AddSupplierDrawer onClose={() => setShowAddSupplier(false)} />}
    </>
  );
}
