import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { useToast } from '../components/Toast';
import { api, type TeamMember } from '../lib/api';
import { useTeam, useAddTeamMember } from '../lib/queries';
import { useQueryClient } from '@tanstack/react-query';

const DELEGATED = [
  ['SALE_RETURN', 'Process returns'],
  ['INVENTORY_ADJUST', 'Adjust stock'],
  ['PRODUCT_EDIT', 'Edit products and prices'],
  ['SUPPLIER_VIEW', 'View suppliers'],
  ['REPORT_VIEW', 'View reports'],
  ['REPORT_FINANCIAL', 'View profit and cost reports'],
  ['EXPENSE_CREATE', 'Record expenses'],
] as const;

function AccessDrawer({ member, onClose }: { member: TeamMember; onClose: () => void }) {
  const showToast = useToast();
  const qc = useQueryClient();
  const [permissions, setPermissions] = useState<string[]>(member.permissions || []);
  const [busy, setBusy] = useState('');

  async function toggle(permission: string, granted: boolean) {
    setBusy(permission);
    try {
      const next = await api.team.setPermission(member.id, permission, granted);
      setPermissions(next);
      qc.invalidateQueries({ queryKey: ['team'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Could not change access');
    } finally {
      setBusy('');
    }
  }

  return (
    <Drawer title={`${member.name} access`} onClose={onClose}>
      <p style={{ fontSize: 13, color: 'var(--ink-soft)' }}>
        {member.role} already has a default set of access. Tick a box to grant more, or clear it to take that access away.
      </p>
      {DELEGATED.map(([permission, label]) => (
        <label key={permission} style={{ display: 'flex', gap: 8, alignItems: 'center', padding: '8px 0', fontSize: 14 }}>
          <input
            type="checkbox"
            checked={permissions.includes(permission)}
            disabled={busy === permission}
            onChange={(e) => void toggle(permission, e.target.checked)}
          />
          {label}
        </label>
      ))}
      <button
        className="btn btn-ghost"
        style={{ marginTop: 12 }}
        onClick={() => {
          const enable = member.status === 'Disabled' || member.status === 'Locked' || member.status === 'Deactivated';
          api.team.setActive(member.id, enable).then(() => { qc.invalidateQueries({ queryKey: ['team'] }); onClose(); showToast(enable ? 'Login enabled' : 'Login disabled'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not change login'));
        }}
      >
        {member.status === 'Active' ? 'Disable login' : member.status === 'Invited' ? 'Cancel invitation' : 'Enable login'}
      </button>
    </Drawer>
  );
}

function AddTeamMemberDrawer({ onClose, showToast }: { onClose: () => void; showToast: (m: string) => void }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState('Cashier');
  const [code, setCode] = useState('');
  const addMember = useAddTeamMember();

  return (
    <Drawer
      title="Invite team member"
      onClose={onClose}
      footer={
        code ? (
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={onClose}>
            Done
          </button>
        ) : (
          <>
            <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              style={{ flex: 1 }}
              disabled={!name || phone.length < 8 || addMember.isPending}
              onClick={() =>
                addMember.mutate(
                  { name, phone, role },
                  {
                    onSuccess: (member) => {
                      setCode(member.invitationCode || '');
                      showToast('Invitation created');
                    },
                    onError: (err) => showToast(err instanceof Error ? err.message : 'Could not invite team member'),
                  }
                )
              }
            >
              {addMember.isPending ? 'Saving…' : 'Create invitation'}
            </button>
          </>
        )
      }
    >
      {code ? (
        <div style={{ padding: '12px 14px', background: 'var(--surface-alt)', border: '1px solid var(--border)', borderRadius: 10 }}>
          <p style={{ marginTop: 0 }}>Give this code to {name}. They choose their own PIN. It works once and expires in 48 hours.</p>
          <p style={{ fontSize: 28, fontWeight: 700, letterSpacing: 2 }}>{code}</p>
        </div>
      ) : (
        <>
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
              <option>Inventory</option>
              <option>Manager</option>
            </select>
          </div>
          <div style={{ padding: '12px 14px', background: 'var(--surface-alt)', border: '1px solid var(--border)', borderRadius: 10, fontSize: 12.5, color: 'var(--ink-soft)' }}>
            You do not set their PIN. They enter this invitation on the login screen and choose one themselves.
          </div>
        </>
      )}
    </Drawer>
  );
}

export default function Team() {
  const [show, setShow] = useState(false);
  const [access, setAccess] = useState<TeamMember | null>(null);
  const showToast = useToast();
  const { data: members, isLoading, isError } = useTeam();

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-spacer" />
        <button className="btn btn-accent" onClick={() => setShow(true)}>
          <Icons.plus size={15} color="#3A2405" /> Invite team member
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
                      <span className="pill pill-neutral">{m.status}</span>
                    )}
                    <button className="btn btn-ghost btn-sm" style={{ marginLeft: 8 }} onClick={() => setAccess(m)}>Access</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {show && <AddTeamMemberDrawer onClose={() => setShow(false)} showToast={showToast} />}
      {access && <AccessDrawer member={access} onClose={() => setAccess(null)} />}
    </>
  );
}
