const BASE_URL = () => {
  const desktop = (window as Window & { dukaDesktop?: { apiBase?: string } }).dukaDesktop?.apiBase;
  const configured = desktop && desktop.length > 0 ? desktop : import.meta.env.VITE_API_URL || 'http://localhost:4000/api';
  return configured.replace(/\/$/, '');
};

export function apiBase() {
  return BASE_URL();
}

export function authToken() {
  return localStorage.getItem('duka.token');
}

export function setAuthToken(token: string | null) {
  if (token) localStorage.setItem('duka.token', token);
  else localStorage.removeItem('duka.token');
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (!(options?.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const token = authToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${BASE_URL()}${path}`, { ...options, headers });
  if (res.status === 401 && !path.startsWith('/auth/')) {
    setAuthToken(null);
    if (!window.location.pathname.startsWith('/login')) window.location.assign('/login');
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed: ${res.status}`);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function downloadExport(kind: string) {
  const headers = new Headers();
  const token = authToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${BASE_URL()}/business/export/${kind}`, { headers });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Export failed: ${res.status}`);
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${kind}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

// ---- Types (mirrors server/index.ts) ----
export type Product = {
  id: number;
  name: string;
  sku: string;
  barcode?: string | null;
  price: number;
  cost: number | null;
  stock: number;
  reorder: number;
  cat: string;
  emoji: string;
  color: string;
  unit?: string;
  taxRate?: number;
  active?: boolean;
  brand?: string;
  taxCategory?: string;
  supplierId?: number | null;
  categoryId?: number | null;
  parentCategory?: string | null;
};

export type Customer = { id: number; name: string; phone: string; purchases: number; total: number; balance: number };
export type Supplier = {
  id: number;
  name: string;
  contact: string;
  products: string;
  phone?: string;
  email?: string;
  address?: string;
  category?: string;
  notes?: string;
  active?: boolean;
  balance?: number;
};
export type SupplierOffer = { id: number; productId: number; productName: string; supplierSku: string; alias: string; unitCost: number };
export type SupplierHistory = { id: number; kind: string; reference: string; total: number | null; status: string; at: string };
export type SupplierDetail = { supplier: Supplier; offers: SupplierOffer[]; history: SupplierHistory[] };
export type Category = { id: number; name: string; parentId?: number | null };
export type Branch = { id: number; name: string; sales: number; staff: number; lowStock: number };
export type Warehouse = { id: number; name: string; branch: string; stockValue: number };

export type PurchaseOrder = { id: string; supplier: string; items: number; total: number; status: string; supplierId?: number | null; stockApplied?: boolean };

export type Expense = { id: string; amount: number; category: string; method: string; description: string; at: string };

export type SaleItem = { productId: number; name: string; qty: number; price: number; refunded?: boolean };
export type Sale = {
  id: number;
  items: SaleItem[];
  total: number;
  method: string;
  at: string;
  customerId?: number;
  customerName?: string;
  etimsStatus?: 'Accepted' | 'Pending' | 'Failed';
  refunded?: boolean;
  status?: string;
  paymentStatus?: string;
  paymentId?: number;
  paymentRef?: string;
  discount?: number;
  tax?: number;
  subtotal?: number;
};

export type StockMovement = { id: string; productId: number; type: string; qty: number; note: string; at: string };

export type CapabilityKey = 'purchasing' | 'credit' | 'advReports' | 'multiBranch' | 'etims';
export type Capabilities = Record<CapabilityKey, boolean>;
export type PlanOffer = {
  code: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  interval: string;
  status?: string;
  modules: string[];
  branchLimit?: number | null;
  userLimit?: number | null;
  deviceLimit?: number | null;
  productLimit?: number | null;
};
export type SubscriptionPaymentRow = {
  id: number;
  plan: string;
  amount: number;
  currency: string;
  status: string;
  provider: string;
  reference?: string | null;
  createdAt: string;
  verifiedAt?: string | null;
};
export type SubscriptionState = {
  providerConfigured: boolean;
  provider: string;
  status: string;
  plan: string | null;
  planName?: string | null;
  startedAt?: string | null;
  renewsAt?: string | null;
  expiresAt?: string | null;
  graceUntil?: string | null;
  modules: Capabilities;
  message: string;
  payments?: SubscriptionPaymentRow[];
  activationSource?: string;
};

export type TeamMember = { id: string; name: string; phone: string; role: string; status: string; permissions?: string[]; invitationCode?: string | null };
export type AuditEntry = { id: string; who: string; what: string; from: string; at: string };

export type EtimsInfo = {
  connected: boolean;
  configured?: boolean;
  mode?: string;
  message?: string;
  submittedToday: number;
  accepted: number;
  failed: number;
  pending: number;
  logs: { id: string; status: 'Accepted' | 'Pending' | 'Failed'; time: string }[];
};

export type AuthUser = { id: number; name: string; phone: string; role: string; businessId?: number; permissions?: string[] };
export type ExtractionLine = {
  id: number;
  rawName: string;
  quantity: number;
  unitCost: number;
  barcode?: string | null;
  matchedProductId?: number | null;
  matchedProductName?: string | null;
  suggestedProductId?: number | null;
  suggestedProductName?: string | null;
  matchMethod?: string | null;
  confidence?: number | null;
  removed: boolean;
};
export type Extraction = {
  id: number;
  documentId?: number;
  supplierName?: string;
  invoiceNumber?: string;
  status: string;
  notes?: string;
  lines: ExtractionLine[];
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
  byCashier?: { name: string; val: number }[];
};

// ---- API ----
export const api = {
  auth: {
    login: (phone: string, pin: string) =>
      request<{ token: string; user: AuthUser }>('/auth/login', { method: 'POST', body: JSON.stringify({ phone, pin }) }),
    register: (data: { phone: string; pin: string; name?: string; businessName?: string; email?: string; businessType?: string; category?: string }) =>
      request<{ token: string; user: AuthUser }>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
    acceptInvite: (code: string, pin: string) =>
      request<{ token: string; user: AuthUser }>('/auth/invitations/accept', { method: 'POST', body: JSON.stringify({ code, pin }) }),
    requestRecovery: (phone: string) =>
      request<{ message: string; delivery: string }>('/auth/recovery/request', { method: 'POST', body: JSON.stringify({ phone }) }),
    completeRecovery: (code: string, newPin: string) =>
      request<{ token: string; user: AuthUser }>('/auth/recovery/complete', { method: 'POST', body: JSON.stringify({ code, newPin }) }),
  },
  products: {
    list: () => request<Product[]>('/products'),
    byBarcode: (barcode: string) => request<Product>(`/products/barcode/${encodeURIComponent(barcode)}`),
    create: (data: { name: string; price: number; cost?: number; sku?: string; barcode?: string; cat?: string; categoryId?: number; reorder?: number; unit?: string; taxRate?: number; brand?: string; taxCategory?: string; supplierId?: number }) =>
      request<Product>('/products', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: { name?: string; price?: number; cost?: number; cat?: string; categoryId?: number; reorder?: number; unit?: string; taxRate?: number; brand?: string; taxCategory?: string; supplierId?: number; active?: boolean; barcode?: string; sku?: string }) =>
      request<Product>(`/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
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
  categories: {
    list: () => request<Category[]>('/categories'),
    create: (data: { name: string; parentId?: number | null }) => request<Category>('/categories', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: { name: string; parentId?: number | null }) => request<Category>(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    remove: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }).catch((err) => { throw err; }),
    templates: () => request<Record<string, { label: string; nodes: { name: string; children?: string[] }[] }>>('/category-templates'),
    applyTemplate: (key: string) => request<Category[]>(`/category-templates/${encodeURIComponent(key)}/apply`, { method: 'POST' }),
  },
  suppliers: {
    list: (q?: string) => request<Supplier[]>(`/suppliers${q ? `?q=${encodeURIComponent(q)}` : ''}`),
    get: (id: number) => request<SupplierDetail>(`/suppliers/${id}`),
    create: (data: Partial<Supplier> & { name: string }) => request<Supplier>('/suppliers', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: Partial<Supplier> & { name: string }) => request<Supplier>(`/suppliers/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    offer: (id: number, data: { productId: number; supplierSku?: string; alias?: string; unitCost?: number }) =>
      request<SupplierOffer>(`/suppliers/${id}/offers`, { method: 'POST', body: JSON.stringify(data) }),
  },
  purchaseOrders: {
    list: () => request<PurchaseOrder[]>('/purchase-orders'),
    create: (data: { supplier?: string; supplierId?: number; items?: number; total?: number; lines?: { productId: number; qty: number; unitCost?: number }[] }) =>
      request<PurchaseOrder>('/purchase-orders', { method: 'POST', body: JSON.stringify(data) }),
    receive: (id: string) => request<PurchaseOrder>(`/purchase-orders/${encodeURIComponent(id)}/receive`, { method: 'POST' }),
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
    get: (id: number) => request<Sale>(`/sales/${id}`),
    create: (
      data: {
        items: SaleItem[];
        method: string;
        customerId?: number;
        discount?: number;
        tendered?: number;
        payments?: { method: string; amount: number }[];
      },
      idempotencyKey?: string,
      device?: { deviceId: string; installationId: string; businessId: string; clientSaleNo?: string },
    ) =>
      request<Sale>('/sales', {
        method: 'POST',
        body: JSON.stringify(data),
        headers: {
          ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
          ...(device?.deviceId ? { 'X-Duka-Device-Id': device.deviceId } : {}),
          ...(device?.installationId ? { 'X-Duka-Installation-Id': device.installationId } : {}),
          ...(device?.businessId ? { 'X-Duka-Business-Id': device.businessId } : {}),
          ...(device?.clientSaleNo ? { 'X-Duka-Client-Sale-No': device.clientSaleNo } : {}),
        },
      }),
    refund: (id: number, data: { itemIndices: number[]; reason?: string }) =>
      request<Sale>(`/sales/${id}/refund`, { method: 'POST', body: JSON.stringify(data) }),
  },
  reviews: {
    list: () => request<Extraction[]>('/receipt-reviews'),
    get: (id: number) => request<Extraction>(`/receipt-reviews/${id}`),
    upload: (file: File, meta: { supplierName?: string; invoiceNumber?: string }) => {
      const body = new FormData();
      body.append('file', file);
      if (meta.supplierName) body.append('supplierName', meta.supplierName);
      if (meta.invoiceNumber) body.append('invoiceNumber', meta.invoiceNumber);
      return request<Extraction>('/documents', { method: 'POST', body });
    },
    update: (id: number, data: unknown) => request<Extraction>(`/receipt-reviews/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    approve: (id: number) => request<Extraction>(`/receipt-reviews/${id}/approve`, { method: 'POST' }),
    reject: (id: number) => request<Extraction>(`/receipt-reviews/${id}/reject`, { method: 'POST' }),
  },
  mpesa: {
    status: () => request<{ mode: string; message: string }>('/integrations/mpesa'),
    sandboxConfirm: (externalRef: string) =>
      request<Sale>('/integrations/mpesa/sandbox/confirm', { method: 'POST', body: JSON.stringify({ externalRef }) }),
  },
  capabilities: {
    get: () => request<Capabilities>('/capabilities'),
  },
  team: {
    list: () => request<TeamMember[]>('/team'),
    add: (data: { name: string; phone: string; role?: string }) =>
      request<TeamMember>('/team', { method: 'POST', body: JSON.stringify(data) }),
    setActive: (id: string, active: boolean) => request<TeamMember>(`/team/${id}/active?active=${active}`, { method: 'POST' }),
    setPermission: (id: string, permission: string, granted: boolean) =>
      request<string[]>(`/team/${id}/permissions`, { method: 'PUT', body: JSON.stringify({ permission, granted }) }),
  },
  subscription: {
    get: () => request<SubscriptionState>('/subscription'),
    plans: () => request<PlanOffer[]>('/subscription/plans'),
    checkout: (plan: string, email: string, channel: 'hosted' | 'mpesa', phone: string, accountPassword: string) =>
      request<{ authorizationUrl?: string; reference?: string; status?: string; subscriptionStatus?: string; message?: string }>('/subscription/checkout', {
        method: 'POST',
        body: JSON.stringify({ plan, email, channel, phone, accountPassword }),
      }),
    verify: (reference: string) =>
      request<SubscriptionState & { message?: string; reference?: string }>('/subscription/verify', { method: 'POST', body: JSON.stringify({ reference }) }),
    cancel: (accountPassword: string) => request<SubscriptionState>('/subscription/cancel', { method: 'POST', body: JSON.stringify({ accountPassword }) }),
    restore: (accountPassword: string) => request<SubscriptionState>('/subscription/restore', { method: 'POST', body: JSON.stringify({ accountPassword }) }),
  },
  support: {
    list: () => request<{ id: number; topic: string; summary: string; status: string; priority?: string; category?: string; updatedAt: string }[]>('/support'),
    open: (data: { topic: string; message: string; category?: string; priority?: string; diagnostics?: Record<string, string> }) => request<{ id: number; status?: string }>('/support', { method: 'POST', body: JSON.stringify(data) }),
    get: (id: number) => request<{ id: number; topic: string; status: string; priority?: string; category?: string; summary?: string; updatedAt?: string; messages: { authorKind: string; authorName: string; body: string; at: string }[] }>(`/support/${id}`),
    reply: (id: number, message: string) => request<unknown>(`/support/${id}/reply`, { method: 'POST', body: JSON.stringify({ message }) }),
  },
  shifts: {
    open: (data: { openingCash: number; deviceId?: string }) => request<{ id: number; openedAt: string }>('/shifts/open', { method: 'POST', body: JSON.stringify(data) }),
    close: (data: { declaredCash: number; expectedCash?: number; deviceId?: string }) => request<{ id: number; variance: number }>('/shifts/close', { method: 'POST', body: JSON.stringify(data) }),
  },
  devices: {
    heartbeat: (data: {
      deviceId: string;
      installationId: string;
      businessId?: string;
      appVersion: string;
      pendingSync: number;
      failedSync: number;
      lastError?: string;
      printerStatus?: string;
      scannerStatus?: string;
      oldestPendingAt?: string;
    }) => request<{ deviceId: string }>('/devices/heartbeat', { method: 'POST', body: JSON.stringify(data) }),
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
  account: {
    me: () => request<{ id: number; name: string; phone: string; email: string; role: string; status: string; businessId: number; businessName: string; businessStatus: string; passwordSet?: boolean }>('/account/me'),
    updateProfile: (data: { name?: string; email?: string }) => request<unknown>('/account/profile', { method: 'PUT', body: JSON.stringify(data) }),
    changePin: (currentPin: string, newPin: string) => request<{ ok: boolean }>('/account/change-credential', { method: 'POST', body: JSON.stringify({ currentPin, newPin }) }),
    setPassword: (currentPin: string, password: string) => request<{ ok: boolean }>('/account/password', { method: 'POST', body: JSON.stringify({ currentPin, password }) }),
    sessions: () => request<{ id: string; deviceId: string; lastSeenAt: string; revoked: boolean; current: boolean }[]>('/account/sessions'),
    revokeSession: (id: string) => request<unknown>(`/account/sessions/${id}`, { method: 'DELETE' }),
    business: () => request<Record<string, string | number | boolean | null>>('/business'),
    updateBusiness: (data: Record<string, string>) => request<Record<string, string>>('/business/profile', { method: 'PUT', body: JSON.stringify(data) }),
    devices: () => request<{ deviceId: string; name: string; installationId: string; lastSeen: string; revoked: boolean }[]>('/business/devices'),
    revokeDevice: (deviceId: string) => request<unknown>(`/business/devices/${deviceId}/revoke`, { method: 'POST' }),
    transferOwner: (userId: number, pin: string, password: string) => request<unknown>('/business/owner-transfer', { method: 'POST', body: JSON.stringify({ userId, pin, password, confirm: 'TRANSFER' }) }),
    recoveryCode: (userId: number) => request<{ code: string }>('/business/users/' + userId + '/recovery-code', { method: 'POST' }),
    setRole: (id: string, role: string) => request<TeamMember>(`/business/users/${id}/role`, { method: 'PUT', body: JSON.stringify({ role }) }),
    close: (pin: string, password: string) => request<unknown>('/business/close', { method: 'POST', body: JSON.stringify({ pin, password, confirm: 'CLOSE' }) }),
  },
};

