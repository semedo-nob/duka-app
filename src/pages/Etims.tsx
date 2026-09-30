import { useState } from 'react';
import { Drawer } from '../components/Drawer';
import { Metric } from '../components/Shared';
import { ModuleLocked } from '../components/ModuleLocked';
import { useCapabilities, useEtims, useRetryEtims, useUpdateSettings } from '../lib/queries';

function pill(status: string) {
  if (status === 'Accepted') return <span className="pill pill-good">Accepted</span>;
  if (status === 'Failed') return <span className="pill pill-bad">Failed</span>;
  return <span className="pill pill-warn">Pending</span>;
}

function DeviceConfigDrawer({ onClose }: { onClose: () => void }) {
  const [pin, setPin] = useState('');
  const update = useUpdateSettings();
  return (
    <Drawer
      title="Taxpayer PIN"
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" style={{ flex: 1 }} onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            style={{ flex: 1 }}
            disabled={update.isPending || pin.length < 4}
            onClick={() => update.mutate({ section: 'tax', data: { pin } }, { onSuccess: onClose })}
          >
            Save PIN locally
          </button>
        </>
      }
    >
      <div className="form-field">
        <label>Taxpayer PIN</label>
        <input value={pin} onChange={(e) => setPin(e.target.value)} />
      </div>
      <p style={{ fontSize: 13, color: 'var(--ink-soft)' }}>
        This stores the PIN on this server only. Nothing is sent to KRA until an official eTIMS contract is configured.
      </p>
    </Drawer>
  );
}

export default function Etims() {
  const [showConfig, setShowConfig] = useState(false);
  const { data: caps } = useCapabilities();
  const { data: etimsInfo, isLoading } = useEtims();
  const retryEtims = useRetryEtims();

  if (!caps?.etims) {
    return (
      <ModuleLocked
        title="eTIMS isn't on this plan"
        body="Sales can still be completed. Tax invoices stay in the local queue and are not sent to KRA until both the module and an official adapter are configured."
      />
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
            <Metric label="Connection" value={etimsInfo.configured ? 'Ready' : 'Not configured'} dir={etimsInfo.configured ? 'up' : ''} delta={etimsInfo.message || ''} />
            <Metric label="Submitted today" value={etimsInfo.submittedToday} />
            <Metric label="Accepted" value={etimsInfo.accepted} delta="" dir="up" />
            <Metric label="Failed / Pending" value={etimsInfo.failed + etimsInfo.pending} delta={etimsInfo.failed ? 'needs retry' : ''} dir={etimsInfo.failed ? 'down' : 'up'} />
          </div>
          {etimsInfo.failed > 0 && (
            <div className="card panel" style={{ marginBottom: 18 }}>
              <h3 style={{ marginBottom: 6 }}>eTIMS submission issue</h3>
              <p style={{ fontSize: 13, color: 'var(--ink-soft)', margin: '0 0 14px' }}>
                {etimsInfo.failed} invoice{etimsInfo.failed > 1 ? 's are' : ' is'} marked failed. Retry checks the adapter again. It will not mark them accepted unless KRA actually accepts them.
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
