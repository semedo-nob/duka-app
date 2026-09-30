import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { api, authToken, apiBase } from '../lib/api';
import { flushOutbox, publishHeartbeat, syncSnapshot, type SyncSnapshot } from '../lib/sync';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { BottomNav } from './BottomNav';

export function AppShell() {
  const location = useLocation();
  const isSell = location.pathname === '/sell';
  const [sync, setSync] = useState<SyncSnapshot | null>(null);
  const [businessStatus, setBusinessStatus] = useState('');

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      let online = navigator.onLine;
      if (authToken() && online) {
        try {
          const base = apiBase();
          await fetch(`${base}/health`);
        } catch {
          online = false;
        }
      }
      if (authToken() && online) await flushOutbox();
      const snap = await syncSnapshot(online);
      if (authToken() && online) await publishHeartbeat(snap);
      if (!cancelled) setSync(snap);
    };
    if (authToken()) {
      api.account.me().then((me) => { if (!cancelled) setBusinessStatus(me.businessStatus); }).catch(() => undefined);
    }
    void tick();
    const timer = window.setInterval(() => void tick(), 15000);
    window.addEventListener('online', tick);
    window.addEventListener('offline', tick);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('online', tick);
      window.removeEventListener('offline', tick);
    };
  }, []);

  return (
    <div className="app">
      <Sidebar />
      <div className="main">
        <Topbar />
        {businessStatus === 'PENDING_APPROVAL' && (
          <div className="sync-banner">This business is waiting for Duka to approve it. You can open Support. Selling stays paused until then.</div>
        )}
        {businessStatus === 'SUSPENDED' && (
          <div className="sync-banner">This business is suspended. Records stay. Selling and changes stay paused.</div>
        )}
        {sync && (
          <div className="sync-banner">
            {sync.online ? 'Online' : 'Offline'}
            {' · '}
            {sync.pending} waiting
            {' · '}
            {sync.failed} failed
            {sync.lastSuccessAt ? ` · last sync ${new Date(sync.lastSuccessAt).toLocaleTimeString()}` : ''}
            {sync.lastError ? ` · ${sync.lastError}` : ''}
          </div>
        )}
        <div className={`view${isSell ? ' no-pad' : ''}`}>
          <Outlet />
        </div>
        <BottomNav />
      </div>
    </div>
  );
}
