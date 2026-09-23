import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../store/useStore';
import { useUpdateSettings } from '../lib/queries';

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
      <h2>You're ready to sell</h2>
      <p className="lede">
        {setupData.businessName || name} is set up as a {(category || 'shop').toLowerCase()}. Core POS, Inventory,
        Customers and Expenses are active — unlock more anytime from Settings.
      </p>
      <div className="auth-actions">
        <button
          className="btn btn-primary btn-lg"
          style={{ width: '100%' }}
          disabled={updateSettings.isPending}
          onClick={() => {
            updateSettings.mutate(
              { section: 'business', data: { name: setupData.businessName || name, type: setupData.businessType } },
              {
                onSettled: () => {
                  logIn();
                  navigate('/');
                },
              }
            );
          }}
        >
          {updateSettings.isPending ? 'Setting up…' : 'Go to dashboard'}
        </button>
      </div>
    </Shell>
  );
}
