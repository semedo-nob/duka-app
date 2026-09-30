import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { Metric } from '../components/Shared';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { ModuleLocked } from '../components/ModuleLocked';
import { useCustomers, useCreateCustomer, useCustomerSales, useCapabilities, useRecordPayment } from '../lib/queries';

function AddCustomerDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const createCustomer = useCreateCustomer();

  function submit() {
    if (!name) return;
    createCustomer.mutate(
      { name, phone },
      {
        onSuccess: () => {
          onClose();
          showToast('Customer added');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not add customer'),
      }
    );
  }

  return (
    <Drawer
      title="Add customer"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={createCustomer.isPending || !name} onClick={submit}>
            {createCustomer.isPending ? 'Saving…' : 'Save customer'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Customer name</label>
        <input placeholder="e.g. Jane Wambui" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Phone number</label>
        <input placeholder="07XX XXX XXX" value={phone} onChange={(e) => setPhone(e.target.value)} />
      </div>
    </Drawer>
  );
}

export default function Customers() {
  const { data: customers, isLoading, isError } = useCustomers();
  const { data: caps } = useCapabilities();
  const recordPayment = useRecordPayment();
  const showToast = useToast();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showAdd, setShowAdd] = useState(false);

  const activeId = selectedId ?? customers?.[0]?.id ?? null;
  const c = customers?.find((x) => x.id === activeId);
  const { data: custSales, isLoading: salesLoading } = useCustomerSales(activeId);

  if (isLoading) {
    return (
      <div className="empty">
        <p>Loading customers…</p>
      </div>
    );
  }
  if (isError || !customers) {
    return (
      <div className="empty">
        <h3>Couldn't reach the server</h3>
        <p>Make sure the API is running (npm run server).</p>
      </div>
    );
  }

  return (
    <>
      <div className="toolbar" style={{ marginBottom: 12 }}>
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShowAdd(true)}>
          <Icons.plus size={15} color="#3A2405" /> Add customer
        </button>
      </div>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="detail-wrap" style={{ height: 'calc(100vh - 230px)' }}>
          <div className="detail-list">
            {customers.map((cust) => (
              <div
                key={cust.id}
                className={`detail-list-item${cust.id === activeId ? ' active' : ''}`}
                onClick={() => setSelectedId(cust.id)}
              >
                <div className="n">{cust.name}</div>
                <div className="s">{cust.phone || 'No phone'}</div>
              </div>
            ))}
          </div>
          {c && (
            <div className="detail-main">
              <h2 className="page-title">{c.name}</h2>
              <p className="page-desc">{c.phone || 'No phone number'}</p>
              <div className="metric-grid" style={{ gridTemplateColumns: 'repeat(3,1fr)', maxWidth: 520 }}>
                <Metric label="Total purchases" value={money(c.total)} />
                <Metric label="Visits" value={c.purchases} />
                <Metric
                  label="Outstanding"
                  value={money(c.balance)}
                  delta={c.balance > 0 ? 'Follow up' : 'Settled'}
                  dir={c.balance > 0 ? 'down' : 'up'}
                />
              </div>

              {caps?.credit ? (
                <>
                  <h3 style={{ fontSize: 14, margin: '24px 0 12px' }}>Customer credit</h3>
                  <div className="card panel" style={{ maxWidth: 520 }}>
                    <div className="tot-row">
                      <span>Credit limit</span>
                      <span>{money(c.balance > 0 ? 30000 : 20000)}</span>
                    </div>
                    <div className="tot-row">
                      <span>Outstanding balance</span>
                      <span>{money(c.balance)}</span>
                    </div>
                    <div className="tot-row grand">
                      <span>Available credit</span>
                      <span>{money((c.balance > 0 ? 30000 : 20000) - c.balance)}</span>
                    </div>
                    <button
                      className="btn btn-ghost btn-sm"
                      style={{ marginTop: 12 }}
                      disabled={c.balance <= 0 || recordPayment.isPending}
                      onClick={() =>
                        recordPayment.mutate(
                          { id: c.id, amount: c.balance },
                          { onSuccess: () => showToast('Payment recorded'), onError: () => showToast('Could not record payment') }
                        )
                      }
                    >
                      Record payment
                    </button>
                  </div>
                </>
              ) : (
                <ModuleLocked title="Customer credit isn't on this plan" body="You can still keep customer names. Selling on credit stays locked until the subscription includes it." />
              )}

              <h3 style={{ fontSize: 14, margin: '24px 0 12px' }}>Recent transactions</h3>
              <div className="card">
                {salesLoading && <p style={{ padding: 16, fontSize: 13, color: 'var(--ink-faint)' }}>Loading sales…</p>}
                {!salesLoading && (!custSales || custSales.length === 0) && (
                  <p style={{ padding: 16, fontSize: 13, color: 'var(--ink-faint)' }}>No sales recorded for this customer yet.</p>
                )}
                {custSales && custSales.length > 0 && (
                  <table>
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Method</th>
                        <th>Items</th>
                        <th>Total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {custSales.map((s) => (
                        <tr key={s.id}>
                          <td>{new Date(s.at).toLocaleDateString('en-KE', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</td>
                          <td>
                            <span className="pill pill-neutral">{s.method}</span>
                          </td>
                          <td>{s.items.length} items</td>
                          <td>{money(s.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
      {showAdd && <AddCustomerDrawer onClose={() => setShowAdd(false)} showToast={showToast} />}
    </>
  );
}
