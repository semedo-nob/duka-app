import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, downloadExport } from '../lib/api';
import { useStore } from '../store/useStore';
import { useToast } from '../components/Toast';

const EXPORTS = ['products', 'categories', 'customers', 'suppliers', 'sales', 'purchases', 'expenses', 'movements', 'audit'];

export default function Account() {
  const navigate = useNavigate();
  const role = useStore((s) => s.user?.role);
  const showToast = useToast();
  const [me, setMe] = useState<Awaited<ReturnType<typeof api.account.me>> | null>(null);
  const [business, setBusiness] = useState<Record<string, string | number | boolean | null> | null>(null);
  const [sessions, setSessions] = useState<Awaited<ReturnType<typeof api.account.sessions>>>([]);
  const [devices, setDevices] = useState<Awaited<ReturnType<typeof api.account.devices>>>([]);
  const [name, setName] = useState('');
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [businessName, setBusinessName] = useState('');
  const [legalName, setLegalName] = useState('');
  const [address, setAddress] = useState('');
  const [kraPin, setKraPin] = useState('');
  const [transferId, setTransferId] = useState('');
  const [transferPin, setTransferPin] = useState('');
  const [closePin, setClosePin] = useState('');
  const [accountPassword, setAccountPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const owner = role === 'OWNER';

  async function load() {
    const profile = await api.account.me();
    setMe(profile);
    setName(profile.name);
    const rows = await api.account.sessions();
    setSessions(rows);
    if (owner || role === 'MANAGER') {
      const biz = await api.account.business();
      setBusiness(biz);
      setBusinessName(String(biz.name || ''));
      setLegalName(String(biz.legalName || ''));
      setAddress(String(biz.address || ''));
      setKraPin(String(biz.kraPin || ''));
      if (owner) setDevices(await api.account.devices());
    }
  }

  useEffect(() => {
    void load().catch((err) => showToast(err instanceof Error ? err.message : 'Could not load account'));
  }, []);

  return (
    <div className="page">
      <h2 style={{ marginTop: 0 }}>My account</h2>
      <p>This is your personal login. Subscription, modules, and support for this business are linked here so they stay in one place.</p>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        <button className="btn btn-ghost" onClick={() => navigate('/settings/billing')}>Subscription</button>
        <button className="btn btn-ghost" onClick={() => navigate('/support')}>Support</button>
        <button className="btn btn-ghost" onClick={() => navigate('/diagnostics')}>Diagnostics</button>
      </div>
      {me && (
        <div className="card" style={{ padding: 16, maxWidth: 560 }}>
          <p>{me.phone} · {me.role} · {me.status}</p>
          <div className="form-field">
            <label>Name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <button className="btn btn-primary" onClick={() => api.account.updateProfile({ name }).then(() => showToast('Profile saved')).catch((err) => showToast(err instanceof Error ? err.message : 'Could not save'))}>
            Save profile
          </button>
          <h3>Change PIN</h3>
          <div className="form-field">
            <label>Current PIN</label>
            <input type="password" value={currentPin} onChange={(e) => setCurrentPin(e.target.value)} />
          </div>
          <div className="form-field">
            <label>New PIN</label>
            <input type="password" value={newPin} onChange={(e) => setNewPin(e.target.value)} />
          </div>
          <button className="btn btn-ghost" disabled={newPin.length < 4 || !currentPin} onClick={() => api.account.changePin(currentPin, newPin).then(() => { setCurrentPin(''); setNewPin(''); showToast('PIN changed. Other sessions were signed out.'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not change PIN'))}>
            Change PIN
          </button>
          {(owner || role === 'MANAGER') && (
            <>
              <h3>Account password</h3>
              <p>Cashiers keep using a PIN at the till. Billing, ownership transfer, and closing the business need this password. Duka never shows a stored PIN.</p>
              <div className="form-field">
                <label>Current PIN</label>
                <input type="password" value={accountPassword} onChange={(e) => setAccountPassword(e.target.value)} />
              </div>
              <div className="form-field">
                <label>New account password</label>
                <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
              </div>
              <button className="btn btn-ghost" disabled={newPassword.length < 8 || !accountPassword} onClick={() => api.account.setPassword(accountPassword, newPassword).then(() => { setAccountPassword(''); setNewPassword(''); showToast(me.passwordSet ? 'Account password updated.' : 'Account password set.'); }).catch((err) => showToast(err instanceof Error ? err.message : 'Could not set the password'))}>
                {me.passwordSet ? 'Change account password' : 'Set account password'}
              </button>
            </>
          )}
          <h3>Sessions</h3>
          {sessions.map((session) => (
            <div key={session.id} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
              <span>{session.current ? 'This device' : session.deviceId || 'Session'} · {session.revoked ? 'Revoked' : 'Active'}</span>
              {!session.revoked && !session.current && (
                <button className="btn btn-ghost btn-sm" onClick={() => api.account.revokeSession(session.id).then(load).catch((err) => showToast(err instanceof Error ? err.message : 'Could not revoke'))}>
                  Revoke
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {(owner || role === 'MANAGER') && business && (
        <>
          <h2>Business account</h2>
          <div className="card" style={{ padding: 16, maxWidth: 560 }}>
            <p>Status {String(business.status)} · Owner {String(business.ownerName || '')}</p>
            <div className="form-field">
              <label>Business name</label>
              <input value={businessName} onChange={(e) => setBusinessName(e.target.value)} disabled={!owner} />
            </div>
            <div className="form-field">
              <label>Legal name</label>
              <input value={legalName} onChange={(e) => setLegalName(e.target.value)} disabled={!owner} />
            </div>
            <div className="form-field">
              <label>Address</label>
              <input value={address} onChange={(e) => setAddress(e.target.value)} disabled={!owner} />
            </div>
            <div className="form-field">
              <label>KRA PIN</label>
              <input value={kraPin} onChange={(e) => setKraPin(e.target.value)} disabled={!owner} />
            </div>
            {owner && (
              <button className="btn btn-primary" onClick={() => api.account.updateBusiness({ name: businessName, legalName, address, kraPin }).then(() => showToast('Business profile saved')).catch((err) => showToast(err instanceof Error ? err.message : 'Could not save'))}>
                Save business
              </button>
            )}
            <h3>Export</h3>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {EXPORTS.map((kind) => (
                <button key={kind} className="btn btn-ghost btn-sm" disabled={!owner} onClick={() => downloadExport(kind).catch((err) => showToast(err instanceof Error ? err.message : 'Export failed'))}>
                  {kind}
                </button>
              ))}
            </div>
            {!owner && <p>Only the owner can export or change the business profile.</p>}
          </div>
        </>
      )}

      {owner && (
        <div className="card" style={{ padding: 16, maxWidth: 560, marginTop: 16 }}>
          <h3 style={{ marginTop: 0 }}>Devices</h3>
          {devices.length === 0 && <p>No devices have checked in yet.</p>}
          {devices.map((device) => (
            <div key={device.deviceId} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
              <span>{device.name || device.deviceId} · {device.revoked ? 'Revoked' : 'Active'}</span>
              {!device.revoked && (
                <button className="btn btn-ghost btn-sm" onClick={() => api.account.revokeDevice(device.deviceId).then(load).catch((err) => showToast(err instanceof Error ? err.message : 'Could not revoke'))}>
                  Revoke
                </button>
              )}
            </div>
          ))}
          <h3>Transfer ownership</h3>
          <p>The new owner must already be an active staff member. You become a manager. This is not a platform admin change.</p>
          <div className="form-field">
            <label>Staff user id</label>
            <input value={transferId} onChange={(e) => setTransferId(e.target.value)} />
          </div>
          <div className="form-field">
            <label>Your PIN</label>
            <input type="password" value={transferPin} onChange={(e) => setTransferPin(e.target.value)} />
          </div>
          <div className="form-field">
            <label>Account password</label>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </div>
          <button className="btn btn-ghost" onClick={() => api.account.transferOwner(Number(transferId), transferPin, newPassword).then(() => showToast('Ownership transferred. Sign in again to refresh your role.')).catch((err) => showToast(err instanceof Error ? err.message : 'Could not transfer'))}>
            Transfer ownership
          </button>
          <h3>Close business</h3>
          <p>Closing keeps sales, stock history, and audit records for seven years. Selling stops. Nothing is deleted now.</p>
          <div className="form-field">
            <label>Your PIN</label>
            <input type="password" value={closePin} onChange={(e) => setClosePin(e.target.value)} />
          </div>
          <button className="btn btn-ghost" onClick={() => api.account.close(closePin, newPassword).then(load).catch((err) => showToast(err instanceof Error ? err.message : 'Could not close'))}>
            Close business
          </button>
        </div>
      )}
    </div>
  );
}
