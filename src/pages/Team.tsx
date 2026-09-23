import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { useToast } from '../components/Toast';
import { useTeam, useAddTeamMember } from '../lib/queries';

function AddTeamMemberDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState('Cashier');
  const addMember = useAddTeamMember();

  return (
    <Drawer
      title="Add team member"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            style={{ flex: 1 }}
            disabled={!name || addMember.isPending}
            onClick={() =>
              addMember.mutate(
                { name, phone, role },
                {
                  onSuccess: () => {
                    onClose();
                    showToast('Invite sent');
                  },
                  onError: (err) => showToast(err instanceof Error ? err.message : 'Could not add team member'),
                }
              )
            }
          >
            {addMember.isPending ? 'Sending…' : 'Send invite'}
          </button>
        </>
      }
    >
      <div className="form-row2">
        <div className="form-field">
          <label>Full name</label>
          <input placeholder="e.g. Grace Wambui" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Phone</label>
          <input placeholder="07XX XXX XXX" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
      </div>
      <div className="form-field">
        <label>Role</label>
        <select value={role} onChange={(e) => setRole(e.target.value)}>
          <option>Cashier</option>
          <option>Storekeeper</option>
          <option>Manager</option>
          <option>Owner</option>
        </select>
      </div>
      <div style={{ padding: '12px 14px', background: 'var(--surface-alt)', border: '1px solid var(--border)', borderRadius: 10, fontSize: 12.5, color: 'var(--ink-soft)' }}>
        Cashiers can sell and take payment. Discounts, refunds, price changes, stock adjustments, voids and cash
        adjustments need manager authorization by default — adjust this anytime under permissions.
      </div>
    </Drawer>
  );
}

export default function Team() {
  const [show, setShow] = useState(false);
  const showToast = useToast();
  const { data: members, isLoading, isError } = useTeam();

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShow(true)}>
          <Icons.plus size={15} color="#3A2405" /> Add team member
        </button>
      </div>
      <div className="card">
        {isLoading && (
          <div className="empty">
            <p>Loading team…</p>
          </div>
        )}
        {isError && (
          <div className="empty">
            <h3>Couldn't reach the server</h3>
            <p>Make sure the API is running (npm run server).</p>
          </div>
        )}
        {members && (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Role</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.id}>
                  <td style={{ fontWeight: 700 }}>{m.name}</td>
                  <td>
                    <span className="pill pill-neutral">{m.role}</span>
                  </td>
                  <td>
                    {m.status === 'Active' ? (
                      <span className="pill pill-good">Active</span>
                    ) : (
                      <span className="pill pill-neutral">Off shift</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {show && <AddTeamMemberDrawer onClose={() => setShow(false)} showToast={showToast} />}
    </>
  );
}
