const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:4000/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed: ${res.status}`);
  }
  return res.json();
}

// ---- Types (mirrors server/index.ts) ----
export type Product = {
  id: number;
  name: string;
  sku: string;
  price: number;
  cost: number;
  stock: number;
  reorder: number;
  cat: string;
  emoji: string;
  color: string;
};

export type Customer = { id: number; name: string; phone: string; purchases: number; total: number; balance: number };
export type Supplier = { id: number; name: string; contact: string; products: string };
export type Branch = { id: number; name: string; sales: number; staff: number; lowStock: number };
export type Warehouse = { id: number; name: string; branch: string; stockValue: number };

export type PurchaseOrder = { id: string; supplier: string; items: number; total: number; status: 'Delivered' | 'Awaiting delivery' };

export type Expense = { id: string; amount: number; category: string; method: string; description: string; at: string };

export type SaleItem = { productId: number; name: string; qty: number; price: number };
export type Sale = { id: number; items: SaleItem[]; total: number; method: string; at: string; customerId?: number; customerName?: string; etimsStatus?: 'Accepted' | 'Pending' | 'Failed'; refunded?: boolean };

export type StockMovement = { id: string; productId: number; type: string; qty: number; note: string; at: string };

export type CapabilityKey = 'purchasing' | 'credit' | 'advReports' | 'multiBranch' | 'etims';
export type Capabilities = Record<CapabilityKey, boolean>;

export type TeamMember = { id: string; name: string; phone: string; role: string; status: 'Active' | 'Off shift' };
export type AuditEntry = { id: string; who: string; what: string; from: string; at: string };

export type EtimsInfo = {
  connected: boolean;
  submittedToday: number;
  accepted: number;
  failed: number;
  pending: number;
  logs: { id: string; status: 'Accepted' | 'Pending' | 'Failed'; time: string }[];
};

export type Settings = {
  business: { name: string; type: string; address: string; phone: string };
  payments: { mpesaTill: string; cardTerminal: string; defaultMethod: string };
  receipts: { footer: string; showLogo: string; copies: string };
  tax: { pin: string; taxCategory: string };
};

export type DashboardData = {
  totalSales: number;
  transactions: number;
  itemsSold: number;
  grossProfit: number;
  cash: number;
  mpesa: number;
  card: number;
  lowStockCount: number;
  refundedTotal: number;
  alerts: { kind: string; title: string; sub: string }[];
  trend: number[];
};

export type ReportsData = {
  totalSales: number;
  transactions: number;
  itemsSold: number;
  avgTransaction: number;
  byMethod: { label: string; val: number }[];
  topProducts: { name: string; val: number }[];
};

// ---- API ----
export const api = {
  products: {
    list: () => request<Product[]>('/products'),
    create: (data: { name: string; price: number; cost?: number; sku?: string; cat?: string; reorder?: number }) =>
      request<Product>('/products', { method: 'POST', body: JSON.stringify(data) }),
    receive: (id: number, data: { qty: number; cost?: number; supplier?: string }) =>
      request<Product>(`/products/${id}/receive`, { method: 'POST', body: JSON.stringify(data) }),
    adjust: (id: number, data: { delta: number; reason?: string }) =>
      request<Product>(`/products/${id}/adjust`, { method: 'POST', body: JSON.stringify(data) }),
    movements: (id: number) => request<StockMovement[]>(`/products/${id}/movements`),
  },
  customers: {
    list: () => request<Customer[]>('/customers'),
    create: (data: { name: string; phone?: string }) =>
      request<Customer>('/customers', { method: 'POST', body: JSON.stringify(data) }),
    sales: (id: number) => request<Sale[]>(`/customers/${id}/sales`),
    recordPayment: (id: number, amount: number) =>
      request<Customer>(`/customers/${id}/payments`, { method: 'POST', body: JSON.stringify({ amount }) }),
  },
  suppliers: {
    list: () => request<Supplier[]>('/suppliers'),
    create: (data: { name: string; contact?: string; products?: string }) =>
      request<Supplier>('/suppliers', { method: 'POST', body: JSON.stringify(data) }),
  },
  purchaseOrders: {
    list: () => request<PurchaseOrder[]>('/purchase-orders'),
    create: (data: { supplier: string; items: number; total: number }) =>
      request<PurchaseOrder>('/purchase-orders', { method: 'POST', body: JSON.stringify(data) }),
  },
  branches: {
    list: () => request<Branch[]>('/branches'),
    create: (data: { name: string }) => request<Branch>('/branches', { method: 'POST', body: JSON.stringify(data) }),
  },
  warehouses: {
    list: () => request<Warehouse[]>('/warehouses'),
    transfer: (data: { from: string; to: string; amount: number }) =>
      request<{ success: boolean }>('/warehouses/transfer', { method: 'POST', body: JSON.stringify(data) }),
  },
  expenses: {
    list: () => request<Expense[]>('/expenses'),
    create: (data: { amount: number; category: string; method: string; description?: string }) =>
      request<Expense>('/expenses', { method: 'POST', body: JSON.stringify(data) }),
  },
  etims: {
    get: () => request<EtimsInfo>('/etims'),
    retry: () => request<{ success: boolean }>('/etims/retry', { method: 'POST' }),
  },
  sales: {
    list: () => request<Sale[]>('/sales'),
    create: (data: { items: SaleItem[]; method: string; customerId?: number }) =>
      request<Sale>('/sales', { method: 'POST', body: JSON.stringify(data) }),
    refund: (id: number, data: { itemIndices: number[]; reason?: string }) =>
      request<Sale>(`/sales/${id}/refund`, { method: 'POST', body: JSON.stringify(data) }),
  },
  capabilities: {
    get: () => request<Capabilities>('/capabilities'),
    unlock: (key: CapabilityKey) => request<Capabilities>(`/capabilities/${key}/unlock`, { method: 'POST' }),
  },
  team: {
    list: () => request<TeamMember[]>('/team'),
    add: (data: { name: string; phone?: string; role?: string }) =>
      request<TeamMember>('/team', { method: 'POST', body: JSON.stringify(data) }),
  },
  audit: {
    list: () => request<AuditEntry[]>('/audit'),
  },
  settings: {
    get: () => request<Settings>('/settings'),
    update: <K extends keyof Settings>(section: K, data: Partial<Settings[K]>) =>
      request<Settings[K]>(`/settings/${section}`, { method: 'PUT', body: JSON.stringify(data) }),
  },
  dashboard: {
    get: () => request<DashboardData>('/dashboard'),
  },
  reports: {
    get: (period?: string) => request<ReportsData>(`/reports${period ? `?period=${encodeURIComponent(period)}` : ''}`),
  },
};

