import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { syncSnapshot } from '../lib/sync';

const TOPICS = [
  ['SUBSCRIPTION', 'Subscription'],
  ['BILLING', 'Billing'],
  ['PAYMENT', 'Payment'],
  ['TECHNICAL', 'Something is not working'],
  ['ACCOUNT', 'Account'],
  ['DEVICE', 'This till'],
  ['PRINTER', 'Printer'],
  ['SCANNER', 'Scanner'],
  ['M-PESA', 'M-Pesa'],
  ['ETIMS', 'eTIMS'],
  ['SYNC', 'Sales not uploading'],
  ['OFFLINE', 'Offline sales'],
  ['OTHER', 'Other'],
] as const;

const TECHNICAL = new Set(['TECHNICAL', 'DEVICE', 'PRINTER', 'SCANNER', 'SYNC', 'OFFLINE', 'M-PESA', 'ETIMS']);

export default function Support() {
  const [topic, setTopic] = useState<string>(TOPICS[0][0]);
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [priority, setPriority] = useState('NORMAL');
  const [status, setStatus] = useState('');
  const [cases, setCases] = useState<{ id: number; topic: string; status: string; summary: string; priority?: string; updatedAt?: string }[]>([]);
  const [openId, setOpenId] = useState<number | null>(null);
  const [thread, setThread] = useState<{ authorKind: string; authorName: string; body: string; at?: string }[]>([]);
  const [caseStatus, setCaseStatus] = useState('');
  const [reply, setReply] = useState('');

  async function load() {
    setCases(await api.support.list());
  }

  useEffect(() => {
    void load().catch(() => setStatus('Could not load your tickets. Try again in a moment.'));
    const timer = window.setInterval(() => {
      void load().catch(() => undefined);
      if (openId) void show(openId).catch(() => undefined);
    }, 20000);
    return () => window.clearInterval(timer);
  }, [openId]);

  async function show(id: number) {
    setOpenId(id);
    const detail = await api.support.get(id);
    setThread(detail.messages);
    setCaseStatus(detail.status);
  }

  async function create() {
    const diagnostics = TECHNICAL.has(topic) ? await deviceContext() : undefined;
    await api.support.open({ topic: subject.trim() || TOPICS.find(([value]) => value === topic)?.[1] || topic, message, category: topic, priority, diagnostics });
    setMessage('');
    setSubject('');
    setStatus('Ticket opened. Status: OPEN.');
    await load();
  }

  const selected = cases.find((item) => item.id === openId);
  const last = [...thread].reverse().find((item) => item.authorKind !== 'CONTEXT');

  return (
    <>
      <p className="page-desc" style={{ marginTop: -14 }}>
        Messages here go to Duka support. You can see the reply and the status on the same ticket.
      </p>
      <div className="card" style={{ padding: 16, maxWidth: 560 }}>
        <h3 style={{ marginTop: 0 }}>Create ticket</h3>
        <div className="form-field">
          <label>Subject</label>
          <input value={subject} onChange={(event) => setSubject(event.target.value)} placeholder="Short summary" />
        </div>
        <div className="form-field">
          <label>Category</label>
          <select value={topic} onChange={(event) => setTopic(event.target.value)}>
            {TOPICS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="form-field">
          <label>Description</label>
          <textarea value={message} onChange={(event) => setMessage(event.target.value)} rows={4} />
        </div>
        <div className="form-field">
          <label>Priority</label>
          <select value={priority} onChange={(event) => setPriority(event.target.value)}>
            <option value="LOW">Low</option>
            <option value="NORMAL">Normal</option>
            <option value="HIGH">High</option>
            <option value="URGENT">Urgent</option>
          </select>
        </div>
        <button className="btn btn-primary" disabled={message.trim().length < 5} onClick={() => void create().catch(() => setStatus('Could not send the ticket. Try again.'))}>
          Create ticket
        </button>
        {status && <p style={{ fontSize: 13 }}>{status}</p>}
      </div>
      <h3>My tickets</h3>
      <div className="card">
        {cases.length === 0 && <p style={{ padding: 16, fontSize: 13 }}>No tickets yet.</p>}
        {cases.map((item) => (
          <div key={item.id} style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
            <strong>{item.topic}</strong>
            <div style={{ fontSize: 13, color: 'var(--ink-soft)' }}>{item.status}{item.priority ? ` · ${item.priority}` : ''}</div>
            <div style={{ fontSize: 13 }}>{item.summary}</div>
            <button className="btn btn-ghost btn-sm" style={{ marginTop: 8 }} onClick={() => void show(item.id).catch(() => setStatus('Could not open this ticket.'))}>
              {openId === item.id ? 'Open' : 'View'}
            </button>
          </div>
        ))}
      </div>
      {selected && (
        <div className="card" style={{ padding: 16, marginTop: 12, maxWidth: 640 }}>
          <strong>{selected.topic}</strong>
          <p style={{ fontSize: 13, color: 'var(--ink-soft)' }}>Status {caseStatus || selected.status}{last ? ` · last response ${last.authorKind === 'PLATFORM' ? 'from Duka support' : 'from you'}` : ''}</p>
          {thread.filter((item) => item.authorKind !== 'CONTEXT').map((item, index) => (
            <p key={index} style={{ fontSize: 13 }}>
              <strong>{item.authorKind === 'PLATFORM' ? 'Duka support' : item.authorName}:</strong> {item.body}
            </p>
          ))}
          <textarea value={reply} onChange={(event) => setReply(event.target.value)} rows={3} />
          <button className="btn btn-ghost btn-sm" disabled={reply.trim().length < 2} onClick={() => api.support.reply(selected.id, reply).then(() => { setReply(''); return show(selected.id); }).catch(() => setStatus('Could not send the reply'))}>
            Send reply
          </button>
        </div>
      )}
    </>
  );
}

async function deviceContext(): Promise<Record<string, string>> {
  try {
    const snap = await syncSnapshot(navigator.onLine);
    const desktop = (window as Window & { dukaDesktop?: { printerStatus?: () => Promise<string> } }).dukaDesktop;
    let printer = 'Not reported';
    if (desktop?.printerStatus) {
      printer = await desktop.printerStatus().catch(() => 'Not reported');
    }
    return {
      'App version': '0.3.0',
      Device: snap.deviceId || '',
      Installation: snap.installationId || '',
      'Last sync': snap.lastSuccessAt || '',
      'Waiting to sync': String(snap.pending),
      'Failed sync': String(snap.failed),
      'Last error': snap.lastError || '',
      Printer: printer,
    };
  } catch {
    return { 'App version': '0.3.0' };
  }
}
