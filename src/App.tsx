import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { useStore } from './store/useStore';
import Login from './pages/Login';
import Setup from './pages/Setup';
import Dashboard from './pages/Dashboard';
import Sell from './pages/Sell';
import Products from './pages/Products';
import Inventory from './pages/Inventory';
import Customers from './pages/Customers';
import Purchasing from './pages/Purchasing';
import Expenses from './pages/Expenses';
import Branches from './pages/Branches';
import Reports from './pages/Reports';
import Team from './pages/Team';
import Etims from './pages/Etims';
import Audit from './pages/Audit';
import ReceiptReview from './pages/ReceiptReview';
import Settings from './pages/Settings';
import Diagnostics from './pages/Diagnostics';
import Billing from './pages/Billing';
import Support from './pages/Support';
import Account from './pages/Account';
import Join from './pages/Join';
import { PlatformAdmins, PlatformAudit, PlatformBilling, PlatformBusiness, PlatformBusinesses, PlatformDashboard, PlatformLogin, PlatformPlans, PlatformShell, PlatformSubscriptions, PlatformSupport } from './platform/pages';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const loggedIn = useStore((s) => s.loggedIn);
  if (!loggedIn) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/join" element={<Join />} />
        <Route path="/setup" element={<Setup />} />
        <Route path="/platform/login" element={<PlatformLogin />} />
        <Route path="/platform" element={<PlatformShell />}>
          <Route index element={<PlatformDashboard />} />
          <Route path="businesses" element={<PlatformBusinesses />} />
          <Route path="businesses/:id" element={<PlatformBusiness />} />
          <Route path="support" element={<PlatformSupport />} />
          <Route path="subscriptions" element={<PlatformSubscriptions />} />
          <Route path="plans" element={<PlatformPlans />} />
          <Route path="billing" element={<PlatformBilling />} />
          <Route path="audit" element={<PlatformAudit />} />
          <Route path="admins" element={<PlatformAdmins />} />
        </Route>
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Dashboard />} />
          <Route path="/sell" element={<Sell />} />
          <Route path="/products" element={<Products />} />
          <Route path="/inventory" element={<Inventory />} />
          <Route path="/receipts" element={<ReceiptReview />} />
          <Route path="/customers" element={<Customers />} />
          <Route path="/purchasing" element={<Purchasing />} />
          <Route path="/expenses" element={<Expenses />} />
          <Route path="/branches" element={<Branches />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/team" element={<Team />} />
          <Route path="/etims" element={<Etims />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/account" element={<Account />} />
          <Route path="/diagnostics" element={<Diagnostics />} />
          <Route path="/settings/billing" element={<Billing />} />
          <Route path="/settings/support" element={<Support />} />
          <Route path="/settings/diagnostics" element={<Diagnostics />} />
          <Route path="/support" element={<Support />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
