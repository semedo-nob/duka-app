import { useEffect, useMemo, useState } from 'react';
import { NavLink, Navigate, Outlet, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { platformApi, platformToken, setPlatformToken, type PlatformSession } from './client';
import { ConfirmDialog, DataTable, EmptyState, ErrorState, LoadingState, Metric, MetricGroup, PageHeader, StatusBadge, Tabs, formatCell, when } from './ui';
import './platform.css';

const MODULE_LABELS: Record<string, string> = {
  purchasing: 'Purchasing',
  credit: 'Customer credit',
  advReports: 'Advanced reports',
  multiBranch: 'Multi-branch',
  etims: 'eTIMS',
};
const MODULES = Object.keys(MODULE_LABELS);
const SUPPORT_STATUSES = ['OPEN', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];
const NAV = [
  ['/platform', 'Dashboard', false],
  ['/platform/businesses', 'Businesses', false],
  ['/platform/subscriptions', 'Subscriptions', 'billing'],
  ['/platform/plans', 'Plans', 'billing'],
  ['/platform/billing', 'Billing', 'money'],
  ['/platform/support', 'Support', 'support'],
  ['/platform/audit', 'Audit', false],
  ['/platform/admins', 'Platform users', 'super'],
] as const;

type Me = PlatformSession | null;
type Row = Record<string, unknown>;

function useMe() {
  return useOutletContext<Me>();
}

export function PlatformLogin() {
  const navigate = useNavigate();
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  if (platformToken()) return <Navigate to="/platform" replace />;
  return (
    <div className="ops-login">
      <form className="card" onSubmit={(event) => {
        event.preventDefault();
        setError('');
        platformApi.login(phone, pin).then((result) => {
          setPlatformToken(result.token);
          navigate('/platform');
        }).catch((err) => setError(err instanceof Error ? err.message : 'Login failed'));
      }}>
        <div className="ops-brand" style={{ color: 'var(--ink)', padding: 0 }}>
          <strong>Duka Platform</strong>
          <span style={{ color: 'var(--ink-soft)' }}>Operator console</span>
        </div>
        <h1>Sign in</h1>
        <p>This login is for Duka operators. It is not a shop till. Development uses phone 0700000000 and PIN 123456, and that pair is refused outside the dev and test profiles.</p>
        <label>Phone<input value={phone} onChange={(event) => setPhone(event.target.value)} autoComplete="username" /></label>
        <label>PIN<input type="password" value={pin} onChange={(event) => setPin(event.target.value)} autoComplete="current-password" /></label>
        {error && <ErrorState message={error} />}
        <button className="btn btn-primary" type="submit">Sign in</button>
      </form>
    </div>
  );
}

export function PlatformShell() {
  const [me, setMe] = useState<Me>(null);
  const [error, setError] = useState('');
  const [menu, setMenu] = useState(false);
  useEffect(() => {
    if (!platformToken()) return;
    platformApi.me().then(setMe).catch((err) => setError(err instanceof Error ? err.message : 'Could not load session'));
  }, []);
  if (!platformToken()) return <Navigate to="/platform/login" replace />;
  const role = me?.role || '';
  const visible = NAV.filter(([, , gate]) => {
    if (!gate) return true;
    if (gate === 'super') return role === 'SUPER_ADMIN';
    if (gate === 'support') return role === 'SUPER_ADMIN' || role === 'SUPPORT_ADMIN';
    if (gate === 'money') return role === 'SUPER_ADMIN' || role === 'BILLING_ADMIN';
    return role === 'SUPER_ADMIN' || role === 'BILLING_ADMIN' || role === 'PLATFORM_AUDITOR';
  });
  return (
    <div className="platform-shell">
      <aside className="ops-side">
        <div className="ops-brand">
          <strong>Duka Platform</strong>
          <span>Operations</span>
        </div>
        {visible.map(([path, label]) => (
          <NavLink key={path} to={path} end={path === '/platform'}>{label}</NavLink>
        ))}
        <div className="ops-side-foot" />
      </aside>
      <div className="ops-main">
        <header className="ops-top">
          <h1>Duka Platform</h1>
          <div className="ops-account">
            {error && <ErrorState message={error} />}
            <div className="ops-account-meta">
              <strong>{me?.name || 'Operator'}</strong>
              <span>{role ? role.replaceAll('_', ' ') : 'Signing in'}</span>
            </div>
            <div className="ops-menu">
              <button className="ops-avatar" type="button" aria-label="Account menu" onClick={() => setMenu((open) => !open)}>
                {(me?.name || 'A').slice(0, 1).toUpperCase()}
              </button>
              {menu && (
                <div className="ops-menu-pop">
                  <button className="btn btn-ghost" type="button" onClick={() => { setPlatformToken(null); window.location.assign('/platform/login'); }}>Sign out</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <div className="ops-content">
          <Outlet context={me} />
        </div>
      </div>
    </div>
  );
}

export function PlatformDashboard() {
  const [data, setData] = useState<Row | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    platformApi.dashboard().then((rows) => setData(rows as Row)).catch((err) => setError(err instanceof Error ? err.message : 'Could not load dashboard'));
  }, []);
  if (error) return <ErrorState message={error} />;
  if (!data) return <LoadingState label="Loading operations…" />;
  const num = (key: string) => Number(data[key] ?? 0);
  return (
    <>
      <PageHeader title="Dashboard" crumb="Platform" />
      <p className="ops-muted" style={{ marginTop: -8 }}>
        Billing provider {String(data.billingProvider || 'unconfigured')}
        {data.billingConfigured ? ' · Paystack secret is set' : ' · Paystack is not ready to take a live charge'}
      </p>
      <MetricGroup title="Businesses">
        <Metric label="Businesses" value={num('businesses')} />
        <Metric label="Active" value={num('activeBusinesses')} />
        <Metric label="Pending approval" value={num('pendingApproval')} />
        <Metric label="Suspended" value={num('suspendedBusinesses')} />
      </MetricGroup>
      <MetricGroup title="Subscriptions">
        <Metric label="Trial" value={num('trialBusinesses')} />
        <Metric label="Active" value={num('activeSubscriptions')} />
        <Metric label="Past due" value={num('pastDueSubscriptions')} />
        <Metric label="Expired" value={num('expiredSubscriptions')} />
      </MetricGroup>
      <MetricGroup title="Billing">
        <Metric label="Successful payments" value={num('successfulPayments')} />
        <Metric label="Pending payments" value={num('pendingPayments')} />
        <Metric label="Failed payments" value={num('failedPayments')} />
      </MetricGroup>
      <MetricGroup title="Support">
        <Metric label="Open" value={num('openTickets')} />
        <Metric label="In progress" value={num('inProgressTickets')} />
        <Metric label="Waiting for customer" value={num('waitingTickets')} />
      </MetricGroup>
      <MetricGroup title="Devices">
        <Metric label="Online" value={num('devicesOnline')} />
        <Metric label="Offline" value={num('devicesOffline')} />
        <Metric label="Pending sync" value={num('devicesPendingSync')} />
        <Metric label="Failed sync" value={num('devicesFailedSync')} />
      </MetricGroup>
      <div className="ops-split">
        <section>
          <h2>Recent registrations</h2>
          <DataTable
            rows={asRows(data.recentBusinesses)}
            columns={[
              { key: 'name', label: 'Business' },
              { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
              { key: 'createdAt', label: 'Created', render: (row) => when(row.createdAt) },
            ]}
          />
        </section>
        <section>
          <h2>Recent payments</h2>
          <DataTable
            rows={asRows(data.recentPayments)}
            columns={[
              { key: 'businessId', label: 'Business' },
              { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
              { key: 'provider', label: 'Source' },
              { key: 'createdAt', label: 'When', render: (row) => when(row.createdAt) },
            ]}
          />
        </section>
      </div>
      <div className="ops-split">
        <section>
          <h2>Recent support tickets</h2>
          <DataTable
            rows={asRows(data.recentTickets)}
            columns={[
              { key: 'topic', label: 'Ticket' },
              { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
              { key: 'updatedAt', label: 'Updated', render: (row) => when(row.updatedAt) },
            ]}
          />
        </section>
        <section>
          <h2>Recent platform actions</h2>
          <DataTable
            rows={asRows(data.activity)}
            columns={[
              { key: 'at', label: 'When', render: (row) => when(row.at) },
              { key: 'actor', label: 'Operator' },
              { key: 'action', label: 'Action' },
            ]}
          />
        </section>
      </div>
    </>
  );
}

export function PlatformBusinesses() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [subscription, setSubscription] = useState('ALL');
  const [sort, setSort] = useState('created');
  const [page, setPage] = useState(0);
  useEffect(() => {
    platformApi.businesses().then(setRows).catch((err) => setError(err instanceof Error ? err.message : 'Could not load businesses'));
  }, []);
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const next = rows.filter((row) => {
      const hay = `${row.name} ${row.owner} ${row.ownerPhone}`.toLowerCase();
      if (needle && !hay.includes(needle)) return false;
      if (status !== 'ALL' && row.status !== status) return false;
      if (subscription !== 'ALL' && row.subscriptionStatus !== subscription) return false;
      return true;
    });
    next.sort((a, b) => {
      if (sort === 'name') return String(a.name).localeCompare(String(b.name));
      if (sort === 'activity') return String(b.lastActivity || '').localeCompare(String(a.lastActivity || ''));
      return String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
    });
    return next;
  }, [rows, query, status, subscription, sort]);
  const pageSize = 12;
  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const slice = filtered.slice(page * pageSize, page * pageSize + pageSize);
  return (
    <>
      <PageHeader title="Businesses" crumb="Platform / Businesses" />
      {error && <ErrorState message={error} />}
      <div className="ops-filters">
        <input value={query} placeholder="Search business or owner" onChange={(event) => { setQuery(event.target.value); setPage(0); }} />
        <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
          {['ALL', 'PENDING_APPROVAL', 'ACTIVE', 'TRIAL', 'SUSPENDED', 'SUBSCRIPTION_EXPIRED', 'CLOSED'].map((item) => <option key={item} value={item}>{item === 'ALL' ? 'All statuses' : item}</option>)}
        </select>
        <select value={subscription} onChange={(event) => { setSubscription(event.target.value); setPage(0); }}>
          {['ALL', 'NONE', 'TRIAL', 'ACTIVE', 'PAST_DUE', 'EXPIRED', 'PENDING'].map((item) => <option key={item} value={item}>{item === 'ALL' ? 'All subscriptions' : item}</option>)}
        </select>
        <select value={sort} onChange={(event) => setSort(event.target.value)}>
          <option value="created">Newest</option>
          <option value="name">Name</option>
          <option value="activity">Last activity</option>
        </select>
      </div>
      <DataTable
        rows={slice}
        onRow={(row) => navigate(`/platform/businesses/${row.id}`)}
        columns={[
          { key: 'name', label: 'Business' },
          { key: 'owner', label: 'Owner' },
          { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
          { key: 'plan', label: 'Plan', render: (row) => formatCell(row.plan) },
          { key: 'subscriptionStatus', label: 'Subscription', render: (row) => <StatusBadge value={String(row.subscriptionStatus || 'NONE')} /> },
          { key: 'branches', label: 'Branches' },
          { key: 'devices', label: 'Devices' },
          { key: 'lastActivity', label: 'Last activity', render: (row) => when(row.lastActivity) },
          { key: 'createdAt', label: 'Created', render: (row) => when(row.createdAt) },
        ]}
      />
      <div className="ops-filters" style={{ marginTop: 12 }}>
        <span className="ops-muted">{filtered.length} businesses</span>
        <button className="btn btn-ghost" type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</button>
        <span className="ops-muted">Page {page + 1} of {pages}</span>
        <button className="btn btn-ghost" type="button" disabled={page + 1 >= pages} onClick={() => setPage((current) => current + 1)}>Next</button>
      </div>
    </>
  );
}

export function PlatformBusiness() {
  const me = useMe();
  const { id } = useParams();
  const businessId = Number(id);
  const [tab, setTab] = useState('Overview');
  const [data, setData] = useState<Row | null>(null);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [dialog, setDialog] = useState<'approve' | 'suspend' | 'reactivate' | 'close' | 'grant' | 'recover' | null>(null);
  const [reason, setReason] = useState('');
  const [planCode, setPlanCode] = useState('growth');
  const [days, setDays] = useState('30');
  const [source, setSource] = useState<'ADMIN_GRANT' | 'PROMOTIONAL'>('ADMIN_GRANT');
  const [plans, setPlans] = useState<Row[]>([]);
  const [recovery, setRecovery] = useState('');
  const [busy, setBusy] = useState(false);
  function load() {
    platformApi.business(businessId).then(setData).catch((err) => setError(err instanceof Error ? err.message : 'Could not open business'));
  }
  useEffect(() => { load(); }, [id]);
  useEffect(() => {
    if (me?.role === 'SUPER_ADMIN' || me?.role === 'BILLING_ADMIN') {
      platformApi.plans().then(setPlans).catch(() => undefined);
    }
  }, [me?.role]);
  if (error) return <ErrorState message={error} />;
  if (!data) return <LoadingState label="Loading business…" />;
  const subscription = (data.subscription || {}) as Row;
  const modules = (subscription.modules || {}) as Record<string, boolean>;
  const diagnosis = (data.billingDiagnosis || {}) as Row;
  const payments = Array.isArray(subscription.payments) ? subscription.payments as Row[] : [];
  const canOperate = me?.role === 'SUPER_ADMIN';
  const canBill = me?.role === 'SUPER_ADMIN' || me?.role === 'BILLING_ADMIN';
  const canSupport = me?.role === 'SUPER_ADMIN' || me?.role === 'SUPPORT_ADMIN';
  const status = String(data.status);
  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setNote('');
    try {
      await action();
      setNote(done);
      setDialog(null);
      setReason('');
      load();
    } catch (err) {
      setNote(err instanceof Error ? err.message : 'The action was not saved');
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title={String(data.name)}
        crumb="Platform / Businesses"
        actions={
          <>
            {canOperate && ['PENDING_APPROVAL', 'PENDING_VERIFICATION', 'REGISTERED'].includes(status) && <button className="btn btn-primary" type="button" onClick={() => setDialog('approve')}>Approve</button>}
            {canOperate && ['ACTIVE', 'TRIAL', 'REACTIVATED'].includes(status) && <button className="btn btn-ghost" type="button" onClick={() => setDialog('suspend')}>Suspend</button>}
            {canOperate && status === 'SUSPENDED' && <button className="btn btn-primary" type="button" onClick={() => setDialog('reactivate')}>Reactivate</button>}
            {canOperate && status !== 'CLOSED' && <button className="btn btn-ghost" type="button" onClick={() => setDialog('close')}>Close</button>}
            {canBill && <button className="btn btn-ghost" type="button" onClick={() => setDialog('grant')}>Activate plan</button>}
            {canSupport && <button className="btn btn-ghost" type="button" onClick={() => setDialog('recover')}>Start PIN recovery</button>}
          </>
        }
      />
      <p className="ops-muted" style={{ marginTop: -8 }}>
        <StatusBadge value={status} /> <StatusBadge value={String(data.subscriptionStatus || 'NONE')} /> {String(data.plan || 'no plan')}
      </p>
      {note && <p>{note}</p>}
      {recovery && <p>Give this one-time code to {String(data.owner || 'the owner')}. They choose the new PIN. You will not see that PIN. Code: <strong>{recovery}</strong></p>}
      <Tabs tabs={['Overview', 'Staff', 'Branches', 'Devices', 'Subscription', 'Payments', 'Entitlements', 'Support', 'Diagnostics', 'Audit']} value={tab} onChange={setTab} />
      {tab === 'Overview' && (
        <div className="card ops-panel">
          <div className="ops-kv">
            <span>Owner</span><strong>{String(data.owner || '—')}</strong>
            <span>Phone</span><strong>{String(data.ownerPhone || '—')}</strong>
            <span>Status reason</span><strong>{String(data.statusReason || '—')}</strong>
            <span>Changed by</span><strong>{String(data.statusChangedBy || '—')} · {when(data.statusChangedAt)}</strong>
            <span>Branches</span><strong>{String(data.branches)}</strong>
            <span>Devices</span><strong>{String(data.devices)}</strong>
            <span>Created</span><strong>{when(data.createdAt)}</strong>
            <span>Last activity</span><strong>{when(data.lastActivity)}</strong>
          </div>
        </div>
      )}
      {tab === 'Staff' && <DataTable rows={asRows(data.staff)} columns={keysOf(asRows(data.staff), ['id'])} />}
      {tab === 'Branches' && <DataTable rows={asRows(data.branches)} columns={keysOf(asRows(data.branches), [])} />}
      {tab === 'Devices' && <DataTable rows={asRows(data.devices)} columns={[
        { key: 'name', label: 'Device' },
        { key: 'appVersion', label: 'Version' },
        { key: 'lastSeen', label: 'Last seen', render: (row) => when(row.lastSeen) },
        { key: 'lastSyncAt', label: 'Last sync', render: (row) => when(row.lastSyncAt) },
        { key: 'pendingSync', label: 'Pending' },
        { key: 'failedSync', label: 'Failed' },
        { key: 'lastError', label: 'Last error' },
        { key: 'printerStatus', label: 'Printer' },
      ]} />}
      {tab === 'Subscription' && (
        <div className="card ops-panel">
          <div className="ops-kv">
            <span>Plan</span><strong>{String(subscription.planName || subscription.plan || '—')}</strong>
            <span>Status</span><strong><StatusBadge value={String(subscription.status || 'NONE')} /></strong>
            <span>Source</span><strong><StatusBadge value={String(subscription.activationSource || subscription.provider || 'NONE')} /></strong>
            <span>Started</span><strong>{when(subscription.startedAt)}</strong>
            <span>Renews</span><strong>{when(subscription.renewsAt)}</strong>
            <span>Expires</span><strong>{when(subscription.expiresAt)}</strong>
          </div>
        </div>
      )}
      {tab === 'Payments' && <DataTable rows={payments} columns={[
        { key: 'createdAt', label: 'When', render: (row) => when(row.createdAt) },
        { key: 'plan', label: 'Plan' },
        { key: 'amount', label: 'Amount' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
        { key: 'provider', label: 'Provider' },
        { key: 'reference', label: 'Reference' },
        { key: 'failureReason', label: 'Failure' },
      ]} />}
      {tab === 'Entitlements' && (
        <div className="card ops-panel">
          {MODULES.map((key) => (
            <div key={key} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
              <span>{MODULE_LABELS[key]}{key === 'advReports' ? ' · includes cashier breakdown' : ''}</span>
              <span className={`pill ops-badge ${modules[key] ? 'ops-badge-good' : 'ops-badge-neutral'}`}>{modules[key] ? 'Unlocked' : 'Locked'}</span>
            </div>
          ))}
          <p className="ops-muted">Unlocked means the server currently allows the module. The till cannot turn a locked module on by itself.</p>
        </div>
      )}
      {tab === 'Support' && <DataTable rows={asRows(data.support)} columns={[
        { key: 'topic', label: 'Ticket' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
        { key: 'priority', label: 'Priority' },
        { key: 'updatedAt', label: 'Updated', render: (row) => when(row.updatedAt) },
      ]} />}
      {tab === 'Diagnostics' && <Diagnosis diagnosis={diagnosis} events={asRows(data.billingEvents)} />}
      {tab === 'Audit' && <DataTable rows={asRows(data.audit)} columns={[
        { key: 'at', label: 'When', render: (row) => when(row.at) },
        { key: 'actor', label: 'Actor' },
        { key: 'action', label: 'Action' },
        { key: 'detail', label: 'Detail' },
      ]} />}
      {dialog && dialog !== 'grant' && dialog !== 'recover' && (
        <ConfirmDialog
          title={dialog === 'suspend' ? 'Suspend business' : dialog === 'approve' ? 'Approve business' : dialog === 'reactivate' ? 'Reactivate business' : 'Close business'}
          confirmLabel={dialog === 'suspend' ? 'Suspend' : dialog === 'approve' ? 'Approve' : dialog === 'reactivate' ? 'Reactivate' : 'Close'}
          danger={dialog === 'suspend' || dialog === 'close'}
          busy={busy}
          disabled={reason.trim().length < 3}
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            const call = dialog === 'approve' ? platformApi.approve : dialog === 'suspend' ? platformApi.suspend : dialog === 'reactivate' ? platformApi.reactivate : platformApi.closeBusiness;
            void run(() => call(businessId, reason), 'Saved. The reason is on the audit log.');
          }}
        >
          <p className="ops-muted">{String(data.name)}. This does not delete sales.</p>
          <label>Reason<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Required. Stored on the audit event." /></label>
        </ConfirmDialog>
      )}
      {dialog === 'grant' && (
        <ConfirmDialog
          title="Manual activation"
          confirmLabel="Activate"
          busy={busy}
          disabled={reason.trim().length < 8 || !planCode}
          onCancel={() => setDialog(null)}
          onConfirm={() => void run(() => platformApi.grant(businessId, { planCode, days: Number(days), reason, source }), 'Plan activated. This is not a Paystack payment.')}
        >
          <p className="ops-muted">Business: {String(data.name)}. This writes an administrative subscription. It does not create a Paystack payment.</p>
          <label>Plan
            <select value={planCode} onChange={(event) => setPlanCode(event.target.value)}>
              {plans.filter((plan) => plan.status === 'ACTIVE').map((plan) => <option key={String(plan.code)} value={String(plan.code)}>{String(plan.name)}</option>)}
            </select>
          </label>
          <label>Duration (days)<input value={days} onChange={(event) => setDays(event.target.value)} /></label>
          <label>Source
            <select value={source} onChange={(event) => setSource(event.target.value as 'ADMIN_GRANT' | 'PROMOTIONAL')}>
              <option value="ADMIN_GRANT">Admin grant</option>
              <option value="PROMOTIONAL">Promotional</option>
            </select>
          </label>
          <label>Reason<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Customer paid offline, promotional activation, or support-approved activation" /></label>
        </ConfirmDialog>
      )}
      {dialog === 'recover' && (
        <ConfirmDialog
          title="Start PIN recovery"
          confirmLabel="Issue code"
          busy={busy}
          onCancel={() => setDialog(null)}
          onConfirm={() => void run(async () => {
            const result = await platformApi.ownerRecovery(businessId);
            setRecovery(result.code);
          }, 'Recovery code issued. The owner chooses the new PIN.')}
        >
          <p className="ops-muted">You will see a one-time code to pass to the owner. You will not see the PIN they choose. Existing sessions are revoked when they use the code.</p>
        </ConfirmDialog>
      )}
    </>
  );
}

function Diagnosis({ diagnosis, events }: { diagnosis: Row; events: Row[] }) {
  return (
    <div className="ops-split">
      <div className="card ops-panel">
        <h2>Why a module may still be locked</h2>
        <p>{String(diagnosis.likelyCause || 'No diagnosis yet.')}</p>
        <div className="ops-kv">
          <span>Subscription</span><strong>{String(diagnosis.subscriptionStatus || '—')}</strong>
          <span>Plan</span><strong>{String(diagnosis.plan || '—')}</strong>
          <span>Source</span><strong>{String(diagnosis.activationSource || '—')}</strong>
          <span>Latest payment</span><strong>{diagnosis.latestPayment ? String((diagnosis.latestPayment as Row).status) : 'None'}</strong>
          <span>Webhooks seen</span><strong>{String(diagnosis.webhookCount ?? 0)}</strong>
          <span>Rejected webhooks</span><strong>{String(diagnosis.rejectedWebhooks ?? 0)}</strong>
          <span>Paystack configured</span><strong>{diagnosis.providerConfigured ? 'Yes' : 'No'}</strong>
          <span>Callback URL</span><strong>{String(diagnosis.callbackUrl || 'Not set')}</strong>
          <span>Webhook URL</span><strong>{String(diagnosis.webhookUrl || 'Not set')}</strong>
        </div>
      </div>
      <div>
        <h2>Billing events</h2>
        <DataTable rows={events} columns={[
          { key: 'createdAt', label: 'When', render: (row) => when(row.createdAt) },
          { key: 'provider', label: 'Provider' },
          { key: 'eventType', label: 'Event' },
          { key: 'processed', label: 'Processed', render: (row) => row.processed ? 'Yes' : 'No' },
          { key: 'summary', label: 'Summary' },
        ]} />
      </div>
    </div>
  );
}

export function PlatformSupport() {
  const [rows, setRows] = useState<Row[]>([]);
  const [filter, setFilter] = useState('OPEN');
  const [open, setOpen] = useState<number | null>(null);
  const [thread, setThread] = useState<{ authorKind: string; authorName: string; body: string; at?: string }[]>([]);
  const [context, setContext] = useState<Row | null>(null);
  const [reply, setReply] = useState('');
  const [status, setStatus] = useState('IN_PROGRESS');
  const [error, setError] = useState('');
  function load() {
    platformApi.support().then(setRows).catch((err) => setError(err instanceof Error ? err.message : 'Could not load tickets'));
  }
  useEffect(() => { load(); }, []);
  async function show(id: number) {
    setOpen(id);
    const detail = await platformApi.supportCase(id);
    setThread(detail.messages || []);
    setStatus(detail.status);
    setContext((detail.context || null) as Row | null);
  }
  const visible = rows.filter((row) => filter === 'ALL' || row.status === filter);
  const selected = rows.find((row) => Number(row.id) === open);
  return (
    <>
      <PageHeader title="Support" crumb="Platform / Support" />
      {error && <ErrorState message={error} />}
      <Tabs tabs={['OPEN', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED', 'ALL']} value={filter} onChange={setFilter} />
      <div className="ops-split">
        <DataTable
          rows={visible}
          onRow={(row) => void show(Number(row.id))}
          columns={[
            { key: 'id', label: '#' },
            { key: 'topic', label: 'Subject' },
            { key: 'category', label: 'Category' },
            { key: 'priority', label: 'Priority' },
            { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
            { key: 'businessId', label: 'Business' },
          ]}
        />
        <div className="card ops-panel">
          {!selected && <EmptyState title="Select a ticket" detail="Replies, the business, and device context appear here." />}
          {selected && (
            <>
              <strong>#{String(selected.id)} {String(selected.topic || selected.summary)}</strong>
              <StatusBadge value={String(selected.status)} />
              <div className="ops-thread">
                {thread.filter((message) => message.authorKind !== 'CONTEXT').map((message, index) => (
                  <article key={index} className={message.authorKind === 'PLATFORM' ? 'ops-bubble platform' : 'ops-bubble'}>
                    <header><span>{message.authorKind === 'PLATFORM' ? 'Duka support' : message.authorName}</span><span>{when(message.at)}</span></header>
                    <div>{message.body}</div>
                  </article>
                ))}
              </div>
              {thread.some((message) => message.authorKind === 'CONTEXT') && (
                <pre className="ops-bubble">{thread.filter((message) => message.authorKind === 'CONTEXT').map((message) => message.body).join('\n')}</pre>
              )}
              {context && <p className="ops-muted">Business {String((context.business as Row | undefined)?.name || selected.businessId)} · subscription {String(((context.subscription as Row | undefined)?.status) || '')}</p>}
              <label>Reply<textarea rows={3} value={reply} onChange={(event) => setReply(event.target.value)} /></label>
              <div className="ops-page-actions">
                <button className="btn btn-primary" type="button" disabled={reply.trim().length < 2} onClick={() => platformApi.reply(Number(selected.id), reply).then(() => { setReply(''); return show(Number(selected.id)); }).then(load).catch((err) => setError(err instanceof Error ? err.message : 'Could not reply'))}>Reply</button>
                <select value={status} onChange={(event) => setStatus(event.target.value)}>{SUPPORT_STATUSES.map((item) => <option key={item}>{item}</option>)}</select>
                <button className="btn btn-ghost" type="button" onClick={() => platformApi.status(Number(selected.id), status).then(() => show(Number(selected.id))).then(load).catch((err) => setError(err instanceof Error ? err.message : 'Could not change status'))}>Update status</button>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

export function PlatformSubscriptions() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    platformApi.subscriptions().then(setRows).catch((err) => setError(err instanceof Error ? err.message : 'Could not load subscriptions'));
  }, []);
  return (
    <>
      <PageHeader title="Subscriptions" crumb="Platform / Subscriptions" />
      {error && <ErrorState message={error} />}
      <DataTable rows={rows} columns={[
        { key: 'businessId', label: 'Business' },
        { key: 'plan', label: 'Plan' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
        { key: 'activationSource', label: 'Source', render: (row) => <StatusBadge value={String(row.activationSource || row.provider)} /> },
        { key: 'startedAt', label: 'Started', render: (row) => when(row.startedAt) },
        { key: 'expiresAt', label: 'Expires', render: (row) => when(row.expiresAt) },
      ]} />
    </>
  );
}

export function PlatformPlans() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('0');
  const [interval, setInterval] = useState('MONTH');
  const [status, setStatus] = useState('ACTIVE');
  const [modules, setModules] = useState<string[]>([]);
  const [limits, setLimits] = useState({ branchLimit: '', userLimit: '', deviceLimit: '', productLimit: '' });
  function load() {
    platformApi.plans().then(setRows).catch((err) => setError(err instanceof Error ? err.message : 'Could not load plans'));
  }
  useEffect(() => { load(); }, []);
  function edit(row: Row) {
    setEditing(String(row.code));
    setCode(String(row.code));
    setName(String(row.name || ''));
    setDescription(String(row.description || ''));
    setPrice(String(row.price ?? 0));
    setInterval(String(row.interval || 'MONTH'));
    setStatus(String(row.status || 'ACTIVE'));
    setModules(Array.isArray(row.modules) ? row.modules.map(String) : []);
    setLimits({
      branchLimit: row.branchLimit == null ? '' : String(row.branchLimit),
      userLimit: row.userLimit == null ? '' : String(row.userLimit),
      deviceLimit: row.deviceLimit == null ? '' : String(row.deviceLimit),
      productLimit: row.productLimit == null ? '' : String(row.productLimit),
    });
  }
  function blank() {
    setEditing(null);
    setCode('');
    setName('');
    setDescription('');
    setPrice('0');
    setInterval('MONTH');
    setStatus('ACTIVE');
    setModules([]);
    setLimits({ branchLimit: '', userLimit: '', deviceLimit: '', productLimit: '' });
  }
  const body = {
    code, name, description, price: Number(price), currency: 'KES', interval, status, modules,
    branchLimit: limitOrNull(limits.branchLimit), userLimit: limitOrNull(limits.userLimit),
    deviceLimit: limitOrNull(limits.deviceLimit), productLimit: limitOrNull(limits.productLimit),
  };
  return (
    <>
      <PageHeader title="Plans" crumb="Platform / Plans" actions={<button className="btn btn-ghost" type="button" onClick={blank}>New plan</button>} />
      {error && <ErrorState message={error} />}
      <DataTable rows={rows} onRow={edit} columns={[
        { key: 'name', label: 'Name' },
        { key: 'price', label: 'Price', render: (row) => `${row.currency || 'KES'} ${row.price}` },
        { key: 'interval', label: 'Interval' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
        { key: 'modules', label: 'Entitlements', render: (row) => (Array.isArray(row.modules) ? row.modules : []).map((key) => MODULE_LABELS[String(key)] || String(key)).join(', ') || 'Core only' },
      ]} />
      <form className="card ops-panel ops-form" style={{ marginTop: 16, maxWidth: 560 }} onSubmit={(event) => {
        event.preventDefault();
        platformApi.savePlan(editing == null, body).then(() => { blank(); load(); }).catch((err) => setError(err instanceof Error ? err.message : 'Could not save plan'));
      }}>
        <h2>{editing ? `Edit ${editing}` : 'New plan'}</h2>
        <label>Code<input value={code} disabled={editing != null} onChange={(event) => setCode(event.target.value)} placeholder="growth" /></label>
        <label>Name<input value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label>Description<textarea rows={2} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <label>Price KES<input value={price} onChange={(event) => setPrice(event.target.value)} /></label>
        <label>Billing interval
          <select value={interval} onChange={(event) => setInterval(event.target.value)}><option>MONTH</option><option>YEAR</option></select>
        </label>
        <label>Status
          <select value={status} onChange={(event) => setStatus(event.target.value)}><option>ACTIVE</option><option>INACTIVE</option></select>
        </label>
        {(['branchLimit', 'userLimit', 'deviceLimit', 'productLimit'] as const).map((key) => (
          <label key={key}>{key.replace('Limit', ' limit')}<input value={limits[key]} placeholder="Blank means no limit" onChange={(event) => setLimits((current) => ({ ...current, [key]: event.target.value }))} /></label>
        ))}
        {MODULES.map((key) => (
          <label key={key} style={{ gridTemplateColumns: 'auto 1fr', alignItems: 'center' }}>
            <span><input type="checkbox" checked={modules.includes(key)} onChange={() => setModules((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key])} /> {MODULE_LABELS[key]}</span>
          </label>
        ))}
        <div className="ops-page-actions">
          <button className="btn btn-primary" type="submit">{editing ? 'Save plan' : 'Create plan'}</button>
          {editing && status === 'ACTIVE' && <button className="btn btn-ghost" type="button" onClick={() => platformApi.savePlan(false, { ...body, status: 'INACTIVE' }).then(load).catch((err) => setError(err instanceof Error ? err.message : 'Could not deactivate'))}>Deactivate</button>}
        </div>
      </form>
    </>
  );
}

export function PlatformBilling() {
  const [payments, setPayments] = useState<Row[]>([]);
  const [events, setEvents] = useState<Row[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    Promise.all([platformApi.payments(), platformApi.events()]).then(([pay, ev]) => {
      setPayments(pay);
      setEvents(ev);
    }).catch((err) => setError(err instanceof Error ? err.message : 'Could not load billing'));
  }, []);
  return (
    <>
      <PageHeader title="Billing" crumb="Platform / Billing" />
      {error && <ErrorState message={error} />}
      <h2>Payments</h2>
      <p className="ops-muted">Paystack charges appear here. An admin grant does not create a payment row.</p>
      <DataTable rows={payments} columns={[
        { key: 'createdAt', label: 'When', render: (row) => when(row.createdAt) },
        { key: 'businessId', label: 'Business' },
        { key: 'plan', label: 'Plan' },
        { key: 'amount', label: 'Amount' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={String(row.status)} /> },
        { key: 'provider', label: 'Provider' },
        { key: 'reference', label: 'Reference' },
      ]} />
      <h2 style={{ marginTop: 18 }}>Billing events</h2>
      <DataTable rows={events} columns={[
        { key: 'createdAt', label: 'When', render: (row) => when(row.createdAt) },
        { key: 'provider', label: 'Provider' },
        { key: 'eventType', label: 'Event' },
        { key: 'businessId', label: 'Business' },
        { key: 'summary', label: 'Summary' },
        { key: 'processed', label: 'Processed', render: (row) => row.processed ? 'Yes' : 'No' },
      ]} />
    </>
  );
}

export function PlatformAudit() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    platformApi.audit().then(setRows).catch((err) => setError(err instanceof Error ? err.message : 'Could not load audit'));
  }, []);
  return (
    <>
      <PageHeader title="Audit" crumb="Platform / Audit" />
      {error && <ErrorState message={error} />}
      <DataTable rows={rows} columns={[
        { key: 'at', label: 'When', render: (row) => when(row.at) },
        { key: 'actor', label: 'Actor' },
        { key: 'action', label: 'Action' },
        { key: 'businessId', label: 'Business' },
        { key: 'detail', label: 'Detail' },
      ]} />
    </>
  );
}

export function PlatformAdmins() {
  const [rows, setRows] = useState<{ id: number; name: string; phone: string; role: string; active: boolean }[]>([]);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [pin, setPin] = useState('');
  const [role, setRole] = useState('SUPPORT_ADMIN');
  const [note, setNote] = useState('');
  function load() {
    platformApi.admins().then(setRows).catch((err) => setNote(err instanceof Error ? err.message : 'Could not load platform users'));
  }
  useEffect(() => { load(); }, []);
  return (
    <>
      <PageHeader title="Platform users" crumb="Platform / Users" />
      <DataTable rows={rows as unknown as Row[]} columns={[
        { key: 'name', label: 'Name' },
        { key: 'phone', label: 'Phone' },
        { key: 'role', label: 'Role', render: (row) => <StatusBadge value={String(row.role)} /> },
        { key: 'active', label: 'Active', render: (row) => row.active ? 'Yes' : 'No' },
      ]} />
      <form className="card ops-panel ops-form" style={{ marginTop: 16, maxWidth: 420 }} onSubmit={(event) => {
        event.preventDefault();
        platformApi.createAdmin({ name, phone, pin, role }).then(() => { setPin(''); setNote('Platform user created. The PIN is stored as a hash.'); load(); }).catch((err) => setNote(err instanceof Error ? err.message : 'Could not create'));
      }}>
        <h2>New operator</h2>
        <label>Name<input value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label>Phone<input value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
        <label>PIN<input type="password" value={pin} onChange={(event) => setPin(event.target.value)} /></label>
        <label>Role
          <select value={role} onChange={(event) => setRole(event.target.value)}>
            {['SUPER_ADMIN', 'SUPPORT_ADMIN', 'BILLING_ADMIN', 'PLATFORM_AUDITOR'].map((item) => <option key={item}>{item}</option>)}
          </select>
        </label>
        <button className="btn btn-primary" type="submit">Create</button>
        {note && <p>{note}</p>}
      </form>
    </>
  );
}

function asRows(value: unknown): Row[] {
  return Array.isArray(value) ? value as Row[] : [];
}

function keysOf(rows: Row[], skip: string[]) {
  const keys = rows.length ? Object.keys(rows[0]).filter((key) => !skip.includes(key) && key !== 'messages') : [];
  return keys.map((key) => ({
    key,
    label: key,
    render: key.toLowerCase().includes('at') ? (row: Row) => when(row[key]) : undefined,
  }));
}

function limitOrNull(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}
