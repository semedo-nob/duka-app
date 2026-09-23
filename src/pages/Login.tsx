import { useNavigate } from 'react-router-dom';
import { useStore } from '../store/useStore';

export default function Login() {
  const navigate = useNavigate();
  const logIn = useStore((s) => s.logIn);

  return (
    <div className="auth-root">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="dot" />
          <span>Duka</span>
        </div>
        <h2>Welcome back</h2>
        <p className="lede">Log in to run your business.</p>
        <div className="form-field">
          <label>Phone number</label>
          <input defaultValue="0712 345 678" />
        </div>
        <div className="form-field">
          <label>PIN</label>
          <input type="password" defaultValue="1234" />
        </div>
        <button
          className="btn btn-primary btn-lg"
          style={{ width: '100%' }}
          onClick={() => {
            logIn();
            navigate('/');
          }}
        >
          Log in
        </button>
        <div className="auth-divider">or</div>
        <button className="btn btn-ghost btn-lg" style={{ width: '100%' }} onClick={() => navigate('/setup')}>
          Set up a new business
        </button>
        <div className="auth-foot">
          Trouble logging in? <a>Contact support</a>
        </div>
      </div>
    </div>
  );
}
