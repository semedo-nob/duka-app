import type { ReactNode } from 'react';

export function StatusBadge({ value }: { value: string }) {
  const tone = toneFor(value);
  return <span className={`pill ops-badge ops-badge-${tone}`}>{labelFor(value)}</span>;
}

export function PageHeader({ title, crumb, actions }: { title: string; crumb?: string; actions?: ReactNode }) {
  return (
    <header className="ops-page-head">
      <div>
        {crumb && <div className="ops-crumb">{crumb}</div>}
        <h1>{title}</h1>
      </div>
      {actions && <div className="ops-page-actions">{actions}</div>}
    </header>
  );
}

export function LoadingState({ label }: { label: string }) {
  return <p className="ops-muted">{label}</p>;
}

export function ErrorState({ message }: { message: string }) {
  return <p className="platform-error">{message}</p>;
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="ops-empty card">
      <strong>{title}</strong>
      {detail && <p>{detail}</p>}
    </div>
  );
}

export function MetricGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="ops-group">
      <h2>{title}</h2>
      <div className="ops-metrics">{children}</div>
    </section>
  );
}

export function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="card ops-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function Tabs({ tabs, value, onChange }: { tabs: string[]; value: string; onChange: (tab: string) => void }) {
  return (
    <div className="ops-tabs" role="tablist">
      {tabs.map((tab) => (
        <button key={tab} type="button" role="tab" aria-selected={tab === value} className={tab === value ? 'ops-tab active' : 'ops-tab'} onClick={() => onChange(tab)}>
          {tab}
        </button>
      ))}
    </div>
  );
}

export function DataTable({ columns, rows, onRow }: {
  columns: { key: string; label: string; render?: (row: Record<string, unknown>) => ReactNode }[];
  rows: Record<string, unknown>[];
  onRow?: (row: Record<string, unknown>) => void;
}) {
  if (!rows.length) return <EmptyState title="Nothing to show" />;
  return (
    <div className="card platform-table">
      <table>
        <thead>
          <tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={String(row.id ?? index)} className={onRow ? 'ops-click' : undefined} onClick={onRow ? () => onRow(row) : undefined}>
              {columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : formatCell(row[column.key])}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  danger,
  busy,
  disabled,
  onCancel,
  onConfirm,
}: {
  title: string;
  children: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="ops-backdrop" role="presentation" onClick={onCancel}>
      <form className="card ops-dialog" role="dialog" aria-modal="true" aria-labelledby="ops-dialog-title" onClick={(event) => event.stopPropagation()} onSubmit={(event) => { event.preventDefault(); onConfirm(); }}>
        <h2 id="ops-dialog-title">{title}</h2>
        {children}
        <div className="ops-dialog-actions">
          <button className="btn btn-ghost" type="button" onClick={onCancel}>Cancel</button>
          <button className={danger ? 'btn btn-bad' : 'btn btn-primary'} type="submit" disabled={disabled || busy}>{busy ? 'Working…' : confirmLabel}</button>
        </div>
      </form>
    </div>
  );
}

export function when(value: unknown) {
  if (!value) return '—';
  const date = new Date(String(value));
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

export function formatCell(value: unknown) {
  if (value == null || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

const LABELS: Record<string, string> = {
  PENDING_APPROVAL: 'Pending approval',
  PENDING_VERIFICATION: 'Pending verification',
  SUBSCRIPTION_EXPIRED: 'Subscription expired',
  PAST_DUE: 'Past due',
  IN_PROGRESS: 'In progress',
  WAITING_FOR_CUSTOMER: 'Waiting for customer',
  ADMIN_GRANT: 'Admin grant',
  PAYSTACK_PAYMENT: 'Paystack',
  PROMOTIONAL: 'Promotional',
  SUCCEEDED: 'Successful',
};

function labelFor(value: string) {
  if (!value) return '—';
  return LABELS[value] || value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (char) => char.toUpperCase());
}

function toneFor(value: string) {
  if (['ACTIVE', 'SUCCEEDED', 'RESOLVED', 'ONLINE', 'PAYSTACK_PAYMENT'].includes(value)) return 'good';
  if (['SUSPENDED', 'FAILED', 'EXPIRED', 'CLOSED', 'CANCELLED', 'DEACTIVATED'].includes(value)) return 'bad';
  if (['PENDING', 'PENDING_APPROVAL', 'PAST_DUE', 'OPEN', 'TRIAL', 'WAITING_FOR_CUSTOMER', 'IN_PROGRESS', 'ADMIN_GRANT', 'PROMOTIONAL'].includes(value)) return 'warn';
  return 'neutral';
}
