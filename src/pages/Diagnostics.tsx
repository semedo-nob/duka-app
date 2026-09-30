import { useEffect, useState } from 'react';
import { useStore } from '../store/useStore';
import { ensureIdentity, readMeta, recentLocalSales, requeueFailed } from '../lib/localDb';
import { flushOutbox, syncSnapshot, type SyncSnapshot } from '../lib/sync';
import { printReceipt } from '../lib/receipt';
import { api } from '../lib/api';

export default function Diagnostics() {
  const role = useStore((s) => s.user?.role);
  const businessName = useStore((s) => s.setupData.businessName);
  const [sync, setSync] = useState<SyncSnapshot | null>(null);
  const [etimsPending, setEtimsPending] = useState<string>('Unknown until the server answers');
  const [mpesa, setMpesa] = useState('Unknown until the server answers');
  const [sales, setSales] = useState<Awaited<ReturnType<typeof recentLocalSales>>>([]);
  const [schema, setSchema] = useState('3');
  const [printNote, setPrintNote] = useState('');

  async function testPrint() {
    const desktop = (window as Window & { dukaDesktop?: { testPrint?: () => Promise<{ ok: boolean; detail: string }> } }).dukaDesktop;
    if (!desktop?.testPrint) {
      setPrintNote('This window has no CUPS bridge. A packaged Duka install sends the test to the printer named by DUKA_PRINTER.');
      return;
    }
    const result = await desktop.testPrint();
    setPrintNote(result.ok ? result.detail : `Print failed: ${result.detail}`);
  }

  async function refresh() {
    const online = navigator.onLine;
    try {
      setSync(await syncSnapshot(online));
    } catch (err) {
      setSync({ online: false, pending: 0, failed: 0, lastError: err instanceof Error ? err.message : 'Local database did not open' });
      return;
    }
    setSales(await recentLocalSales());
    const version = await readMeta<string>('schema');
    setSchema(version || '3');
    try {
      const info = await api.etims.get();
      setEtimsPending(`${info.pending} pending, ${info.failed} failed. ${info.message || info.mode || 'Not submitted to KRA.'}`);
      const status = await api.mpesa.status();
      setMpesa(`${status.mode}. ${status.message}`);
    } catch {
      setEtimsPending('Server unreachable. Local sales stay queued.');
      setMpesa('Server unreachable. M-Pesa cannot be confirmed on this device.');
    }
  }

  useEffect(() => {
    void ensureIdentity();
    void refresh();
  }, []);

  if (role === 'CASHIER') {
    return <p>Diagnostics are limited to the owner and managers.</p>;
  }

  return (
    <div className="page">
      <h2 style={{ marginTop: 0 }}>Device diagnostics</h2>
      <p>Application version 0.3.0. Local database schema {schema}.</p>
      <ul>
        <li>Device {sync?.deviceId || '…'}</li>
        <li>Installation {sync?.installationId || '…'}</li>
        <li>Business {sync?.businessId || '…'}</li>
        <li>{sync?.online ? 'Online' : 'Offline'}</li>
        <li>{sync?.pending ?? 0} sales waiting to sync</li>
        <li>{sync?.failed ?? 0} sales failed and kept</li>
        <li>Last successful sync {sync?.lastSuccessAt || 'none yet'}</li>
        <li>Oldest sale still waiting {sync?.oldestPendingAt || 'none'}</li>
        <li>Last error {sync?.lastError || 'none'}</li>
        <li>eTIMS {etimsPending}</li>
        <li>M-Pesa {mpesa}</li>
        <li>Scanner: a USB or Bluetooth keyboard-wedge scanner is read on the Sell screen. An unknown code is shown and is not saved as a product unless a manager fills in the product form.</li>
      </ul>
      <button className="btn btn-ghost" style={{ marginRight: 8 }} onClick={() => void testPrint()}>Test print</button>
      {printNote && <p>{printNote}</p>}
      <button
        className="btn btn-primary"
        onClick={async () => {
          await requeueFailed();
          await flushOutbox({ manual: true });
          await refresh();
        }}
      >
        Retry failed sales
      </button>
      <h3>Receipts on this device</h3>
      {sales.length === 0 && <p>No local sales yet.</p>}
      {sales.map((sale) => (
        <div key={sale.idempotencyKey} style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 8 }}>
          <span>{sale.clientSaleNo} · {sale.method} · {sale.status} · {sale.total}</span>
          <button className="btn btn-ghost" onClick={() => void printReceipt({ businessName: businessName || 'Duka', sale, reprint: true })}>
            Reprint
          </button>
        </div>
      ))}
    </div>
  );
}
