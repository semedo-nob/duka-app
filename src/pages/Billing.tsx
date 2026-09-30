import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api, type PlanOffer } from '../lib/api';
import { useSubscription } from '../lib/queries';

const MODULE_LABELS: Record<string, string> = {
  purchasing: 'Purchasing',
  credit: 'Customer credit',
  advReports: 'Advanced reports',
  multiBranch: 'Multi-branch',
  etims: 'eTIMS queue',
};

export default function Billing() {
  const qc = useQueryClient();
  const { data, refetch } = useSubscription();
  const plans = useQuery({ queryKey: ['plans'], queryFn: api.subscription.plans });
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [channel, setChannel] = useState<'hosted' | 'mpesa'>('mpesa');
  const [accountPassword, setAccountPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState('');

  useEffect(() => {
    const reference = params.get('reference') || params.get('trxref');
    if (!reference) return;
    setBusy('verify');
    api.subscription.verify(reference).then(async (result) => {
      setMessage(result.message || 'Paystack has not confirmed this payment yet.');
      await refetch();
      await qc.invalidateQueries({ queryKey: ['capabilities'] });
    }).catch((err) => setMessage(err instanceof Error ? err.message : 'Could not verify the payment'))
      .finally(() => setBusy(''));
  }, [params, qc, refetch]);

  async function checkout(plan: PlanOffer) {
    setBusy(plan.code);
    setMessage('');
    try {
      const result = await api.subscription.checkout(plan.code, email, channel, phone, accountPassword);
      if (result.authorizationUrl) {
        window.location.assign(result.authorizationUrl);
        return;
      }
      setMessage(result.message || 'The server has not confirmed this payment.');
      await refetch();
      await qc.invalidateQueries({ queryKey: ['capabilities'] });
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Checkout could not start');
    } finally {
      setBusy('');
    }
  }

  async function act(kind: 'cancel' | 'restore') {
    setBusy(kind);
    setMessage('');
    try {
      await (kind === 'cancel' ? api.subscription.cancel(accountPassword) : api.subscription.restore(accountPassword));
      setMessage(kind === 'cancel' ? 'Subscription cancelled. Paid modules are locked. Records were kept.' : 'Subscription restored where the paid period is still open.');
      await refetch();
      await qc.invalidateQueries({ queryKey: ['capabilities'] });
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Could not update the subscription');
    } finally {
      setBusy('');
    }
  }

  return (
    <>
      <p className="page-desc" style={{ marginTop: -14 }}>{data?.message}</p>
      <div className="card" style={{ padding: 16, marginBottom: 16 }}>
        <div style={{ fontWeight: 700 }}>{data?.planName || data?.plan || 'No plan'} · {data?.status || 'NONE'}</div>
        <div style={{ fontSize: 13, color: 'var(--ink-soft)' }}>
          {data?.activationSource === 'ADMIN_GRANT' ? 'Activated by Duka' : data?.activationSource === 'PROMOTIONAL' ? 'Promotional activation' : data?.activationSource === 'PAYSTACK_PAYMENT' ? 'Paid with Paystack' : `Provider ${data?.provider || 'unconfigured'}`}
          {data?.renewsAt ? ` · Renews ${new Date(data.renewsAt).toLocaleDateString()}` : ''}
          {data?.expiresAt ? ` · Expires ${new Date(data.expiresAt).toLocaleDateString()}` : ''}
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          {(data?.status === 'ACTIVE' || data?.status === 'PAST_DUE' || data?.status === 'TRIAL') && (
            <button className="btn btn-ghost" disabled={busy === 'cancel'} onClick={() => void act('cancel')}>Cancel</button>
          )}
          {data?.status === 'CANCELLED' && (
            <button className="btn btn-ghost" disabled={busy === 'restore'} onClick={() => void act('restore')}>Restore</button>
          )}
        </div>
      </div>
      <h3>Available plans</h3>
      <div className="form-field" style={{ maxWidth: 360 }}>
        <label>Email for Paystack</label>
        <input value={email} placeholder="owner@shop.co.ke" onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="form-field" style={{ maxWidth: 360 }}>
        <label>How to pay</label>
        <select value={channel} onChange={(e) => setChannel(e.target.value as 'hosted' | 'mpesa')}>
          <option value="mpesa">M-Pesa prompt from Paystack</option>
          <option value="hosted">Paystack checkout page</option>
        </select>
      </div>
      {channel === 'mpesa' && (
        <div className="form-field" style={{ maxWidth: 360 }}>
          <label>M-Pesa phone</label>
          <input value={phone} placeholder="07…" onChange={(e) => setPhone(e.target.value)} />
        </div>
      )}
      <div className="form-field" style={{ maxWidth: 360 }}>
        <label>Account password</label>
        <input type="password" value={accountPassword} onChange={(e) => setAccountPassword(e.target.value)} />
        <p style={{ fontSize: 12, color: 'var(--ink-soft)' }}>Billing uses the account password, not the till PIN. Set it under Account if you have not yet.</p>
      </div>
      <div className="cap-grid">
        {(plans.data || []).map((plan) => (
          <div key={plan.code} className="card cap-card">
            <div className="cap-top">{data?.plan === plan.code && <span className="pill pill-good">Current</span>}</div>
            <h4>{plan.name}</h4>
            <p>{plan.currency} {Number(plan.price).toLocaleString()} / {plan.interval === 'YEAR' ? 'year' : 'month'}</p>
            <p style={{ fontSize: 13 }}>
              {[
                plan.branchLimit != null ? `${plan.branchLimit} branches` : null,
                plan.userLimit != null ? `${plan.userLimit} users` : null,
                plan.deviceLimit != null ? `${plan.deviceLimit} devices` : null,
                plan.productLimit != null ? `${plan.productLimit} products` : null,
              ].filter(Boolean).join(' · ') || 'No numeric limits on this plan'}
            </p>
            <p>{plan.description}</p>
            <ul>
              {(plan.modules.length ? plan.modules : ['Core selling, inventory, customers, expenses']).map((key) => (
                <li key={key}>{MODULE_LABELS[key] || key}</li>
              ))}
            </ul>
            <button className="btn btn-accent" disabled={!!busy || data?.plan === plan.code && data.status === 'ACTIVE'} onClick={() => void checkout(plan)}>
              {busy === plan.code ? 'Contacting Paystack…' : Number(plan.price) === 0 ? 'Use this plan' : 'Checkout'}
            </button>
          </div>
        ))}
      </div>
      {message && <p style={{ fontSize: 13, marginTop: 12 }}>{message}</p>}
      <h3>Enabled modules</h3>
      <div className="cap-grid">
        {Object.entries(MODULE_LABELS).map(([key, name]) => {
          const active = !!data?.modules?.[key as keyof typeof data.modules];
          return (
            <div key={key} className="card cap-card">
              <div className="cap-top">{active ? <span className="pill pill-good">Unlocked</span> : <span className="pill">Locked</span>}</div>
              <h4>{name}</h4>
            </div>
          );
        })}
      </div>
      <h3>Billing history</h3>
      <div className="card">
        {(data?.payments || []).length === 0 && <p style={{ padding: 16 }}>No subscription payments yet.</p>}
        {(data?.payments || []).map((payment) => (
          <div key={payment.id} style={{ padding: 12, borderBottom: '1px solid var(--border)', fontSize: 13 }}>
            {payment.createdAt ? new Date(payment.createdAt).toLocaleString() : ''} · {payment.currency} {payment.amount} · {payment.status} · {payment.plan}
            {payment.reference ? ` · ${payment.reference}` : ''}
            {payment.verifiedAt ? ' · verified' : ''}
          </div>
        ))}
      </div>
    </>
  );
}
