import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { BottomNav } from './BottomNav';

export function AppShell() {
  const location = useLocation();
  const isSell = location.pathname === '/sell';

  return (
    <div className="app">
      <Sidebar />
      <div className="main">
        <Topbar />
        <div className={`view${isSell ? ' no-pad' : ''}`}>
          <Outlet />
        </div>
        <BottomNav />
      </div>
    </div>
  );
}
