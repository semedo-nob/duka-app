import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useCapabilities, useUnlockCapability, useBranches, useCreateBranch, useWarehouses, useTransferStock } from '../lib/queries';

function AddBranchDrawer({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('');
  const createBranch = useCreateBranch();
  const showToast = useToast();

  function submit() {
    if (!name) return;
    createBranch.mutate(
      { name },
      {
        onSuccess: () => {
          onClose();
          showToast('Branch added');
        },
        onError: (err) => showToast(err instanceof Error ? err.message : 'Could not add branch'),
      }
    );
  }

  return (
    <Drawer
      title="Add branch"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} disabled={createBranch.isPending || !name} onClick={submit}>
            {createBranch.isPending ? 'Saving…' : 'Save branch'}
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Branch name & location</label>
        <input placeholder="e.g. Nakuru — Westside" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
    </Drawer>
  );
}

export default function Branches() {
  const { data: caps } = useCapabilities();
  const unlockCapability = useUnlockCapability();
  const { data: branches, isLoading: branchesLoading } = useBranches();
  const { data: warehouses, isLoading: warehousesLoading } = useWarehouses();
  const transferStock = useTransferStock();
  const [showAdd, setShowAdd] = useState(false);
  const showToast = useToast();

  if (!caps?.multiBranch) {
    return (
      <div className="empty" style={{ maxWidth: 440, margin: '40px auto' }}>
        <Icons.branch size={46} color="var(--ink-faint)" />
        <h3>Running more than one shop?</h3>
        <p>
          Multi-branch adds location switching, per-branch stock, and consolidated reporting — while cashiers keep
          using the same simple POS.
        </p>
        <button className="btn btn-accent" disabled={unlockCapability.isPending} onClick={() => unlockCapability.mutate('multiBranch')}>
          {unlockCapability.isPending ? 'Unlocking…' : 'Unlock Multi-branch'}
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShowAdd(true)}>
          <Icons.plus size={15} color="#3A2405" /> Add branch
        </button>
      </div>
      {branchesLoading && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>Loading branches…</p>}
      {branches && (
        <div className="cap-grid" style={{ gridTemplateColumns: 'repeat(3,1fr)', marginBottom: 24 }}>
          {branches.map((b) => (
            <div key={b.id || b.name} className="card cap-card">
              <div className="cap-top">
                <div className="cap-ic" style={{ background: 'var(--brand-soft)', color: 'var(--brand)' }}>
                  <Icons.box size={17} color="var(--brand)" />
                </div>
                {b.lowStock > 0 ? (
                  <span className="pill pill-warn">{b.lowStock} low stock</span>
                ) : (
                  <span className="pill pill-good">Stocked</span>
                )}
              </div>
              <h4>{b.name}</h4>
              <p>
                {b.staff} team members · {money(b.sales)} in sales today
              </p>
            </div>
          ))}
        </div>
      )}
      <h3 style={{ fontSize: 14.5, margin: '0 0 12px', fontWeight: 800 }}>Warehouses</h3>
      <div className="card">
        {warehousesLoading && <p style={{ padding: 16, fontSize: 13, color: 'var(--ink-faint)' }}>Loading warehouses…</p>}
        {warehouses && (
          <table>
            <thead>
              <tr>
                <th>Warehouse</th>
                <th>Linked branch</th>
                <th>Stock value</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {warehouses.map((w) => (
                <tr key={w.id || w.name}>
                  <td style={{ fontWeight: 700 }}>{w.name}</td>
                  <td>{w.branch}</td>
                  <td>{money(w.stockValue)}</td>
                  <td>
                    <button
                      className="btn btn-ghost btn-sm"
                      disabled={transferStock.isPending}
                      onClick={() =>
                        transferStock.mutate(
                          { from: w.name, to: w.branch, amount: 50000 },
                          { onSuccess: () => showToast(`Transferred stock from ${w.name}`) }
                        )
                      }
                    >
                      Transfer stock
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {showAdd && <AddBranchDrawer onClose={() => setShowAdd(false)} />}
    </>
  );
}
