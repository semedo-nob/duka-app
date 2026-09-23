import { useAudit } from '../lib/queries';

function timeAgo(iso: string) {
  const ms = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(ms / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} hr${hrs > 1 ? 's' : ''} ago`;
  const days = Math.floor(hrs / 24);
  return `${days} day${days > 1 ? 's' : ''} ago`;
}

export default function Audit() {
  const { data: entries, isLoading, isError } = useAudit();

  if (isLoading) {
    return (
      <div className="empty">
        <p>Loading audit log…</p>
      </div>
    );
  }
  if (isError || !entries) {
    return (
      <div className="empty">
        <h3>Couldn't reach the server</h3>
        <p>Make sure the API is running (npm run server).</p>
      </div>
    );
  }
  if (entries.length === 0) {
    return (
      <div className="empty">
        <h3>Nothing logged yet</h3>
        <p>Sensitive actions — refunds, stock adjustments, capability unlocks — will show up here as they happen.</p>
      </div>
    );
  }

  return (
    <div className="card">
      {entries.map((e) => (
        <div className="alert-row" key={e.id} style={{ padding: '14px 20px' }}>
          <span className="alert-dot" style={{ background: 'var(--ink-faint)' }} />
          <div className="text">
            <b>{e.who}</b> {e.what}
            {e.from && <span style={{ color: 'var(--ink-faint)' }}> · {e.from}</span>}
            <br />
            <span style={{ color: 'var(--ink-faint)' }}>{timeAgo(e.at)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
