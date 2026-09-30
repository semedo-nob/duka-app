import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setAuthToken } from '../lib/api';
import { rememberStaffPin } from '../lib/offlineAuth';
import { rememberBusiness } from '../lib/localDb';
import { useStore } from '../store/useStore';

export default function Join() {
  const navigate = useNavigate();
  const logIn = useStore((s) => s.logIn);
  const [code, setCode] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError('');
    try {
      const result = await api.auth.acceptInvite(code, pin);
      setAuthToken(result.token);
      if (result.user.businessId) await rememberBusiness(String(result.user.businessId));
      await rememberStaffPin(result.user, pin);
      logIn(result.user);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not accept the invitation');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-root">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="dot" />
          <span>Duka</span>
        </div>
        <h2>Join this business</h2>
        <p className="lede">Enter the invitation code from the owner, then choose your own PIN.</p>
        <div className="form-field">
          <label>Invitation code</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} />
        </div>
        <div className="form-field">
          <label>New PIN</label>
          <input type="password" value={pin} onChange={(e) => setPin(e.target.value)} />
        </div>
        {error && <p style={{ color: 'var(--bad)', fontSize: 13 }}>{error}</p>}
        <button className="btn btn-primary btn-lg" style={{ width: '100%' }} disabled={busy || code.length < 4 || pin.length < 4} onClick={() => void submit()}>
          {busy ? 'Saving…' : 'Create my login'}
        </button>
        <button className="btn btn-ghost btn-lg" style={{ width: '100%', marginTop: 8 }} onClick={() => navigate('/login')}>
          Back to login
        </button>
      </div>
    </div>
  );
}
