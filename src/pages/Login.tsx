import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../store/useStore';
import { api, setAuthToken } from '../lib/api';
import { rememberStaffPin, unlockOffline } from '../lib/offlineAuth';
import { rememberBusiness } from '../lib/localDb';

export default function Login() {
  const navigate = useNavigate();
  const logIn = useStore((s) => s.logIn);
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState<'login' | 'forgot' | 'code'>('login');
  const [recoveryCode, setRecoveryCode] = useState('');
  const [newPin, setNewPin] = useState('');
  const [recoveryNote, setRecoveryNote] = useState('');

  async function submit() {
    setBusy(true);
    setError('');
    try {
      const result = await api.auth.login(phone, pin);
      setAuthToken(result.token);
      if (result.user.businessId) await rememberBusiness(String(result.user.businessId));
      await rememberStaffPin(result.user, pin);
      logIn(result.user);
      navigate('/');
    } catch (err) {
      const offline = await unlockOffline(phone, pin).catch(() => null);
      if (offline && (err instanceof TypeError || (err instanceof Error && /failed to fetch|network/i.test(err.message)))) {
        setAuthToken(null);
        logIn(offline);
        navigate('/sell');
        return;
      }
      setError(err instanceof Error ? err.message : 'Could not log in');
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
        <h2>{mode === 'forgot' ? 'Forgot your PIN?' : mode === 'code' ? 'Set a new PIN' : 'Welcome back'}</h2>
        <p className="lede">{mode === 'forgot' ? 'Enter your phone number.' : mode === 'code' ? 'Use the code from Duka Support or your business owner.' : 'Log in to run your business.'}</p>
        {mode !== 'code' && (
          <div className="form-field">
            <label>Phone number</label>
            <input value={phone} placeholder="0712 345 678" onChange={(e) => setPhone(e.target.value)} />
          </div>
        )}
        {mode === 'login' && (
          <>
            <div className="form-field">
              <label>PIN</label>
              <input type="password" value={pin} onChange={(e) => setPin(e.target.value)} />
            </div>
            {error && <p style={{ color: 'var(--bad)', fontSize: 13, marginTop: 0 }}>{error}</p>}
            <button className="btn btn-primary btn-lg" style={{ width: '100%' }} disabled={busy || !phone || !pin} onClick={submit}>
              {busy ? 'Checking…' : 'Log in'}
            </button>
            <div className="auth-divider">or</div>
            <button className="btn btn-ghost btn-lg" style={{ width: '100%' }} onClick={() => navigate('/setup')}>
              Set up a new business
            </button>
            <button className="btn btn-ghost" style={{ width: '100%', marginTop: 8 }} onClick={() => navigate('/join')}>
              I have an invitation code
            </button>
          </>
        )}
        {mode === 'login' && (
          <>
            <button className="btn btn-ghost" style={{ width: '100%', marginTop: 8 }} onClick={() => { setError(''); setRecoveryNote(''); setMode('forgot'); }}>
              I forgot my PIN
            </button>
            <button className="btn btn-ghost" style={{ width: '100%', marginTop: 8 }} onClick={() => { setError(''); setMode('code'); }}>
              I have a recovery code
            </button>
          </>
        )}
        {mode === 'forgot' && (
          <>
            <button
              className="btn btn-primary"
              style={{ width: '100%' }}
              onClick={() => {
                if (phone.length < 8) {
                  setRecoveryNote('Enter the phone number on the account.');
                  return;
                }
                api.auth.requestRecovery(phone).then((result) => {
                  setRecoveryNote(result.delivery === 'NOT_CONFIGURED' || result.delivery === 'NOT CONFIGURED'
                    ? 'Online recovery is currently unavailable. Contact Duka Support.'
                    : result.message);
                }).catch(() => setRecoveryNote('Online recovery is currently unavailable. Contact Duka Support.'));
              }}
            >
              Send recovery code
            </button>
            {recoveryNote && <p style={{ fontSize: 13 }}>{recoveryNote}</p>}
            <button className="btn btn-ghost" style={{ width: '100%' }} onClick={() => setMode('login')}>Back to login</button>
          </>
        )}
        {mode === 'code' && (
          <>
            <p className="lede">If Duka Support or your business owner gave you a recovery code, choose a new PIN here. They will not see the PIN.</p>
            <div className="form-field">
              <label>Recovery code</label>
              <input value={recoveryCode} onChange={(e) => setRecoveryCode(e.target.value)} />
            </div>
            <div className="form-field">
              <label>New PIN</label>
              <input type="password" value={newPin} onChange={(e) => setNewPin(e.target.value)} />
            </div>
            <button
              className="btn btn-primary"
              style={{ width: '100%' }}
              disabled={recoveryCode.length < 4 || newPin.length < 4}
              onClick={() => {
                api.auth.completeRecovery(recoveryCode, newPin).then((result) => {
                  setAuthToken(result.token);
                  logIn(result.user);
                  navigate('/');
                }).catch(() => setError('That recovery code was not accepted. Ask Duka Support for a new one.'));
              }}
            >
              Set a new PIN
            </button>
            <button className="btn btn-ghost" style={{ width: '100%' }} onClick={() => setMode('login')}>Back to login</button>
          </>
        )}
      </div>
    </div>
  );
}
