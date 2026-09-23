import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Icons } from '../components/Icons';
import { Metric } from '../components/Shared';
import { useCapabilities, useUnlockCapability, useEtims, useRetryEtims } from '../lib/queries';

function pill(status: string) {
  if (status === 'Accepted') return <span className="pill pill-good">Accepted</span>;
  if (status === 'Failed') return <span className="pill pill-bad">Failed</span>;
  return <span className="pill pill-warn">Pending</span>;
}

function DeviceConfigDrawer({ onClose }: { onClose: () => void }) {
  const [pin, setPin] = useState('P051234567X');
  const [branchConfig, setBranchConfig] = useState('Single device');
  return (
    <Drawer
      title="Device configuration"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={onClose}>
            Save changes
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Taxpayer PIN</label>
        <input value={pin} onChange={(e) => setPin(e.target.value)} />
      </div>
      <div className="form-field">
        <label>Branch configuration</label>
        <select value={branchConfig} onChange={(e) => setBranchConfig(e.target.value)}>
          <option>Single device</option>
          <option>Per-branch devices</option>
        </select>
      </div>
      <div className="form-field">
        <label>Invoice numbering</label>
        <select>
          <option>Automatic (recommended)</option>
          <option>Manual</option>
        </select>
      </div>
      <div style={{ padding: '12px 14px', background: 'var(--surface-alt)', border: '1px solid var(--border)', borderRadius: 10, fontSize: 12.5, color: 'var(--ink-soft)' }}>
        Credit notes for refunds are generated automatically — you won't need to submit these by hand.
      </div>
    </Drawer>
  );
}

export default function Etims() {
  const [showConfig, setShowConfig] = useState(false);
  const { data: caps } = useCapabilities();
  const unlockCapability = useUnlockCapability();
  const { data: etimsInfo, isLoading } = useEtims();
  const retryEtims = useRetryEtims();

  if (!caps?.etims) {
    return (
      <div className="empty" style={{ maxWidth: 440, margin: '40px auto' }}>
        <Icons.doc size={46} color="var(--ink-faint)" />
        <h3>eTIMS isn't connected yet</h3>
        <p>
          Once connected, every sale is submitted to KRA automatically — cashiers never see the technical side,
          only a plain confirmation or a clear message if something needs attention.
        </p>
        <button className="btn btn-accent" disabled={unlockCapability.isPending} onClick={() => unlockCapability.mutate('etims')}>
          {unlockCapability.isPending ? 'Connecting…' : 'Connect eTIMS'}
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="toolbar">
        <div className="toolbar-spacer" />
        <button className="btn btn-ghost" onClick={() => setShowConfig(true)}>
          Device configuration
        </button>
      </div>
      {isLoading && <p style={{ fontSize: 13, color: 'var(--ink-faint)' }}>Loading eTIMS status…</p>}
      {etimsInfo && (
        <>
          <div className="metric-grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 20 }}>
            <Metric label="Connection" value="Connected" dir="up" delta="" />
            <Metric label="Submitted today" value={etimsInfo.submittedToday} />
            <Metric label="Accepted" value={etimsInfo.accepted} delta="" dir="up" />
            <Metric label="Failed / Pending" value={etimsInfo.failed + etimsInfo.pending} delta={etimsInfo.failed ? 'needs retry' : ''} dir={etimsInfo.failed ? 'down' : 'up'} />
          </div>
          {etimsInfo.failed > 0 && (
            <div className="card panel" style={{ marginBottom: 18 }}>
              <h3 style={{ marginBottom: 6 }}>eTIMS submission issue</h3>
              <p style={{ fontSize: 13, color: 'var(--ink-soft)', margin: '0 0 14px' }}>
                We couldn't send {etimsInfo.failed} invoice{etimsInfo.failed > 1 ? 's' : ''} to KRA. Click below to retry manually.
              </p>
              <button
                className="btn btn-primary btn-sm"
                disabled={retryEtims.isPending}
                onClick={() => retryEtims.mutate()}
              >
                {retryEtims.isPending ? 'Retrying…' : 'Retry now'}
              </button>
            </div>
          )}
          <div className="card">
            <table>
              <thead>
                <tr>
                  <th>Sale</th>
                  <th>Status</th>
                  <th>Submitted</th>
                </tr>
              </thead>
              <tbody>
                {etimsInfo.logs.map((r) => (
                  <tr key={r.id}>
                    <td style={{ fontWeight: 700 }}>{r.id}</td>
                    <td>{pill(r.status)}</td>
                    <td>{r.time}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {showConfig && <DeviceConfigDrawer onClose={() => setShowConfig(false)} />}
    </>
  );
}
