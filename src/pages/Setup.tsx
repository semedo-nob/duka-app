import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../store/useStore';
import { useUpdateSettings } from '../lib/queries';
import { api, setAuthToken } from '../lib/api';

const CATS: [string, string][] = [
  ['🛒', 'Grocery / mini-mart'],
  ['💊', 'Pharmacy'],
  ['🍽️', 'Restaurant'],
  ['👗', 'Fashion'],
  ['🔌', 'Electronics'],
  ['📦', 'Something else'],
];
const TYPES = ['Retail shop', 'Mini-mart / supermarket', 'Pharmacy', 'Restaurant / cafe', 'Electronics', 'Other'];

function Shell({ step, children }: { step: number; children: React.ReactNode }) {
  return (
    <div className="auth-root">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="dot" />
          <span>Duka</span>
        </div>
        <div className="setup-steps">
          <div className={`seg${step >= 1 ? ' done' : ''}`} />
          <div className={`seg${step >= 2 ? ' done' : ''}`} />
          <div className={`seg${step >= 3 ? ' done' : ''}`} />
        </div>
        {children}
      </div>
    </div>
  );
}

export default function Setup() {
  const navigate = useNavigate();
  const setupData = useStore((s) => s.setupData);
  const setSetupData = useStore((s) => s.setSetupData);
  const logIn = useStore((s) => s.logIn);
  const updateSettings = useUpdateSettings();
  const [step, setStep] = useState(1);
  const [name, setName] = useState(setupData.businessName);
  const [type, setType] = useState(setupData.businessType);
  const [category, setCategory] = useState<string | null>(setupData.category);
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');

  if (step === 1) {
    return (
      <Shell step={1}>
        <h2>Tell us about your business</h2>
        <p className="lede">This shapes what you see first — you can change it anytime.</p>
        <div className="form-field">
          <label>Business name</label>
          <input placeholder="e.g. Mama Njeri Store" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="form-field">
          <label>Business type</label>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            {TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="auth-actions">
          <button
            className="btn btn-primary btn-lg"
            style={{ width: '100%' }}
            onClick={() => {
              setSetupData({ businessName: name || 'My Business', businessType: type });
              setStep(2);
            }}
          >
            Continue
          </button>
        </div>
      </Shell>
    );
  }

  if (step === 2) {
    return (
      <Shell step={2}>
        <h2>What do you sell?</h2>
        <p className="lede">We'll set up your product categories to match.</p>
        <div className="setup-tile-grid">
          {CATS.map(([emoji, label]) => (
            <button
              key={label}
              className={`setup-tile${category === label ? ' selected' : ''}`}
              onClick={() => setCategory(label)}
            >
              <span className="setup-emoji">{emoji}</span>
              {label}
            </button>
          ))}
        </div>
        <div className="auth-actions">
          <button className="btn btn-ghost btn-lg" style={{ flex: 1 }} onClick={() => setStep(1)}>
            Back
          </button>
          <button
            className="btn btn-primary btn-lg"
            style={{ flex: 1 }}
            disabled={!category}
            onClick={() => {
              setSetupData({ category });
              setStep(3);
            }}
          >
            Continue
          </button>
        </div>
      </Shell>
    );
  }

  return (
    <Shell step={3}>
      <h2>Create the owner login</h2>
      <p className="lede">
        {setupData.businessName || name} stays pending until a Duka platform administrator approves it. You can sign in and contact support. Selling starts after approval.
      </p>
        <div className="form-field">
          <label>Your name</label>
          <input value={ownerName} onChange={(e) => setOwnerName(e.target.value)} placeholder="Amina Njeri" />
        </div>
        <div className="form-field">
          <label>Email</label>
          <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="owner@shop.co.ke" />
        </div>
        <div className="form-field">
          <label>Your phone</label>
          <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="07…" />
        </div>
        <div className="form-field">
          <label>PIN</label>
          <input type="password" value={pin} onChange={(e) => setPin(e.target.value)} />
        </div>
        {error && <p style={{ color: 'var(--bad)', fontSize: 13 }}>{error}</p>}
        <div className="auth-actions">
        <button
          className="btn btn-primary btn-lg"
          style={{ width: '100%' }}
          disabled={updateSettings.isPending || phone.length < 8 || pin.length < 4}
          onClick={async () => {
            try {
              const result = await api.auth.register({
                phone,
                pin,
                name: ownerName || 'Owner',
                businessName: setupData.businessName || name,
                email,
                businessType: setupData.businessType,
                category: category || undefined,
              });
              setAuthToken(result.token);
              logIn(result.user);
              const template = category?.includes('Electronics') ? 'electronics'
                : category?.includes('Pharmacy') ? 'pharmacy'
                : category?.includes('Restaurant') ? 'restaurant'
                : category?.includes('Fashion') ? 'clothing'
                : category?.includes('Grocery') ? 'grocery'
                : 'general';
              await api.categories.applyTemplate(template).catch(() => undefined);
              updateSettings.mutate(
                { section: 'business', data: { name: setupData.businessName || name, type: setupData.businessType } },
                { onSettled: () => navigate('/') },
              );
            } catch (err) {
              setError(err instanceof Error ? err.message : 'Could not create the login');
            }
          }}
        >
          {updateSettings.isPending ? 'Setting up…' : 'Go to dashboard'}
        </button>
      </div>
    </Shell>
  );
}
