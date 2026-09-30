// Retired in-memory prototype. The API is the Spring Boot app in /backend.
// This file is kept so the old experiment is still readable. Do not run it.
import express from 'express';
import cors from 'cors';
import { nanoid } from 'nanoid';

const app = express();
app.use(cors());
app.use(express.json());

// ===================== In-memory data =====================
// This stands in for a real database. Swap these arrays + the handlers below
// for actual DB queries (Postgres/Prisma, SQLite, etc.) when you're ready —
// the REST surface the frontend talks to stays the same.

type Product = {
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

let products: Product[] = [
  { id: 1, name: 'Milk 500ml', sku: 'DAI-001', price: 65, cost: 52, stock: 42, reorder: 20, cat: 'Dairy', emoji: '🥛', color: '#EAF2EE' },
  { id: 2, name: 'White Bread', sku: 'BAK-014', price: 70, cost: 55, stock: 18, reorder: 20, cat: 'Bakery', emoji: '🍞', color: '#FBEDD6' },
  { id: 3, name: 'Sugar 1kg', sku: 'GRO-022', price: 180, cost: 155, stock: 65, reorder: 15, cat: 'Grocery', emoji: '🧂', color: '#EDEBE3' },
  { id: 4, name: 'Cooking Oil 2L', sku: 'GRO-031', price: 520, cost: 470, stock: 24, reorder: 10, cat: 'Grocery', emoji: '🫙', color: '#FBF0DE' },
  { id: 5, name: 'Rice 2kg', sku: 'GRO-018', price: 340, cost: 295, stock: 31, reorder: 12, cat: 'Grocery', emoji: '🍚', color: '#EAF2EE' },
  { id: 6, name: 'Bar Soap', sku: 'HOU-005', price: 60, cost: 44, stock: 80, reorder: 20, cat: 'Household', emoji: '🧼', color: '#E4EEEA' },
  { id: 7, name: 'Maize Flour 2kg', sku: 'GRO-009', price: 215, cost: 185, stock: 8, reorder: 15, cat: 'Grocery', emoji: '🌽', color: '#FBEDD6' },
  { id: 8, name: 'Soda 500ml', sku: 'BEV-002', price: 70, cost: 55, stock: 56, reorder: 24, cat: 'Beverages', emoji: '🥤', color: '#FBE7E3' },
  { id: 9, name: 'Eggs (Tray)', sku: 'DAI-007', price: 420, cost: 370, stock: 14, reorder: 10, cat: 'Dairy', emoji: '🥚', color: '#FBF0DE' },
  { id: 10, name: 'Tea Leaves 100g', sku: 'BEV-011', price: 95, cost: 76, stock: 47, reorder: 15, cat: 'Beverages', emoji: '🍵', color: '#E4EEEA' },
  { id: 11, name: 'Washing Powder 1kg', sku: 'HOU-012', price: 260, cost: 220, stock: 19, reorder: 12, cat: 'Household', emoji: '🧺', color: '#EDEBE3' },
  { id: 12, name: 'Bananas (bunch)', sku: 'GRO-044', price: 120, cost: 90, stock: 26, reorder: 10, cat: 'Grocery', emoji: '🍌', color: '#FBEDD6' },
];
let nextProductId = 13;

type Supplier = { id: number; name: string; contact: string; products: string };
let suppliers: Supplier[] = [
  { id: 1, name: 'Kamau Wholesalers', contact: '0722 100 200', products: 'Grocery, Grains' },
  { id: 2, name: 'Nakumatt Distributors', contact: '0733 400 500', products: 'Household, Beverages' },
];
let nextSupplierId = 3;

type Branch = { id: number; name: string; sales: number; staff: number; lowStock: number };
let branches: Branch[] = [
  { id: 1, name: 'Nairobi — Moi Avenue', sales: 14200, staff: 5, lowStock: 2 },
  { id: 2, name: 'Mombasa — Nyali', sales: 9100, staff: 3, lowStock: 1 },
  { id: 3, name: 'Kisumu — CBD', sales: 5150, staff: 2, lowStock: 0 },
];
let nextBranchId = 4;

type Warehouse = { id: number; name: string; branch: string; stockValue: number };
let warehouses: Warehouse[] = [
  { id: 1, name: 'Nairobi Warehouse', branch: 'Nairobi — Moi Avenue', stockValue: 842000 },
  { id: 2, name: 'Mombasa Warehouse', branch: 'Mombasa — Nyali', stockValue: 310000 },
];

type SaleItem = { productId: number; name: string; qty: number; price: number };
type Sale = { id: number; items: SaleItem[]; total: number; method: string; at: string; customerId?: number; customerName?: string; etimsStatus?: 'Accepted' | 'Pending' | 'Failed'; refunded?: boolean };

function hoursAgo(h: number) {
  return new Date(Date.now() - h * 3600_000).toISOString();
}

let sales: Sale[] = [
  { id: 10478, items: [{ productId: 1, name: 'Milk 500ml', qty: 2, price: 65 }, { productId: 6, name: 'Bar Soap', qty: 1, price: 60 }], total: 190, method: 'cash', at: hoursAgo(6), customerId: 1, customerName: 'Wanjiru Kamau', etimsStatus: 'Accepted' },
  { id: 10479, items: [{ productId: 3, name: 'Sugar 1kg', qty: 1, price: 180 }, { productId: 8, name: 'Soda 500ml', qty: 1, price: 70 }], total: 250, method: 'mpesa', at: hoursAgo(5), customerId: 2, customerName: 'Otieno Mercantile', etimsStatus: 'Failed' },
  { id: 10480, items: [{ productId: 4, name: 'Cooking Oil 2L', qty: 1, price: 520 }], total: 520, method: 'mpesa', at: hoursAgo(4), etimsStatus: 'Accepted' },
  { id: 10481, items: [{ productId: 5, name: 'Rice 2kg', qty: 2, price: 340 }], total: 680, method: 'card', at: hoursAgo(3), customerId: 4, customerName: 'Kiptoo General Store', etimsStatus: 'Accepted' },
  { id: 10482, items: [{ productId: 1, name: 'Milk 500ml', qty: 2, price: 65 }, { productId: 6, name: 'Bar Soap', qty: 1, price: 60 }, { productId: 5, name: 'Rice 2kg', qty: 1, price: 340 }], total: 530, method: 'cash', at: hoursAgo(1), etimsStatus: 'Accepted' },
];
let nextSaleId = 10483;

type CapabilityKey = 'purchasing' | 'credit' | 'advReports' | 'multiBranch' | 'etims';
let capabilities: Record<CapabilityKey, boolean> = {
  purchasing: false,
  credit: false,
  advReports: false,
  multiBranch: false,
  etims: false,
};

type TeamMember = { id: string; name: string; phone: string; role: string; status: 'Active' | 'Off shift' };
let team: TeamMember[] = [
  { id: nanoid(6), name: 'Amina Njeri', phone: '0712 345 678', role: 'Owner', status: 'Active' },
  { id: nanoid(6), name: 'Brian Otieno', phone: '0722 456 789', role: 'Manager', status: 'Active' },
  { id: nanoid(6), name: 'Faith Achieng', phone: '0733 567 890', role: 'Cashier', status: 'Active' },
  { id: nanoid(6), name: 'Peter Mwangi', phone: '0744 678 901', role: 'Cashier', status: 'Off shift' },
];

type AuditEntry = { id: string; who: string; what: string; from: string; at: string };
let auditLog: AuditEntry[] = [
  { id: nanoid(6), who: 'John K.', what: 'voided transaction #10491', from: 'KSh 640', at: hoursAgo(30) },
];
function logAudit(who: string, what: string, from = '') {
  auditLog.unshift({ id: nanoid(6), who, what, from, at: new Date().toISOString() });
}

type Settings = {
  business: { name: string; type: string; address: string; phone: string };
  payments: { mpesaTill: string; cardTerminal: string; defaultMethod: string };
  receipts: { footer: string; showLogo: string; copies: string };
  tax: { pin: string; taxCategory: string };
};
let settings: Settings = {
  business: { name: 'Mama Njeri Store', type: 'Retail shop', address: '', phone: '0712 345 678' },
  payments: { mpesaTill: '', cardTerminal: 'Not connected', defaultMethod: 'Cash' },
  receipts: { footer: 'Asante for shopping with us!', showLogo: 'Yes', copies: '1' },
  tax: { pin: '', taxCategory: 'Standard VAT (16%)' },
};

// ===================== Routes =====================

// Products
app.get('/api/products', (_req, res) => res.json(products));

app.post('/api/products', (req, res) => {
  const { name, price, cost, sku, cat } = req.body;
  if (!name || typeof price !== 'number') {
    return res.status(400).json({ error: 'name and price are required' });
  }
  const product: Product = {
    id: nextProductId++,
    name,
    sku: sku || `SKU-${Date.now()}`,
    price,
    cost: cost ?? Math.round(price * 0.85),
    stock: 0,
    reorder: req.body.reorder ?? 10,
    cat: cat || 'Grocery',
    emoji: '📦',
    color: '#EDEBE3',
  };
  products.push(product);
  logAudit('Amina N.', `added product ${product.name}`);
  res.status(201).json(product);
});

app.post('/api/products/:id/receive', (req, res) => {
  const id = Number(req.params.id);
  const product = products.find((p) => p.id === id);
  if (!product) return res.status(404).json({ error: 'Product not found' });
  const qty = Number(req.body.qty);
  if (!qty || qty <= 0) return res.status(400).json({ error: 'qty must be a positive number' });
  product.stock += qty;
  if (req.body.cost) product.cost = Number(req.body.cost);
  stockMovements.push({
    id: nanoid(6),
    productId: id,
    type: 'received',
    qty,
    note: req.body.supplier || 'Stock received',
    at: new Date().toISOString(),
  });
  logAudit('Amina N.', `received ${qty} units of ${product.name}`, `Supplier: ${req.body.supplier || 'Stock received'}`);
  res.json(product);
});

app.post('/api/products/:id/adjust', (req, res) => {
  const id = Number(req.params.id);
  const product = products.find((p) => p.id === id);
  if (!product) return res.status(404).json({ error: 'Product not found' });
  const delta = Number(req.body.delta);
  if (!delta) return res.status(400).json({ error: 'delta must be a non-zero number' });
  product.stock = Math.max(0, product.stock + delta);
  stockMovements.push({
    id: nanoid(6),
    productId: id,
    type: 'adjusted',
    qty: delta,
    note: req.body.reason || 'Adjustment',
    at: new Date().toISOString(),
  });
  logAudit('Amina N.', `adjusted stock for ${product.name}`, `${delta > 0 ? '+' : ''}${delta} units · ${req.body.reason || 'Adjustment'}`);
  res.json(product);
});

app.get('/api/products/:id/movements', (req, res) => {
  const id = Number(req.params.id);
  res.json(stockMovements.filter((m) => m.productId === id).slice(-20).reverse());
});

// Customers
app.get('/api/customers', (_req, res) => res.json(customers));

app.post('/api/customers', (req, res) => {
  const { name, phone } = req.body;
  if (!name) return res.status(400).json({ error: 'name is required' });
  const cust: Customer = {
    id: Date.now(),
    name,
    phone: phone || '',
    purchases: 0,
    total: 0,
    balance: 0,
  };
  customers.push(cust);
  logAudit('Amina N.', `added new customer ${cust.name}`);
  res.status(201).json(cust);
});

app.get('/api/customers/:id/sales', (req, res) => {
  const id = Number(req.params.id);
  const custSales = sales.filter((s) => s.customerId === id);
  res.json(custSales.slice(-20).reverse());
});

app.post('/api/customers/:id/payments', (req, res) => {
  const id = Number(req.params.id);
  const customer = customers.find((c) => c.id === id);
  if (!customer) return res.status(404).json({ error: 'Customer not found' });
  const amount = Number(req.body.amount);
  if (!amount || amount <= 0) return res.status(400).json({ error: 'amount must be a positive number' });
  customer.balance = Math.max(0, customer.balance - amount);
  logAudit('Amina N.', `recorded credit payment from ${customer.name}`, `KSh ${amount.toLocaleString()}`);
  res.json(customer);
});

// Suppliers
app.get('/api/suppliers', (_req, res) => res.json(suppliers));

app.post('/api/suppliers', (req, res) => {
  const { name, contact, products: prodList } = req.body;
  if (!name) return res.status(400).json({ error: 'name is required' });
  const supplier: Supplier = { id: nextSupplierId++, name, contact: contact || '', products: prodList || '' };
  suppliers.push(supplier);
  logAudit('Amina N.', `added supplier ${supplier.name}`);
  res.status(201).json(supplier);
});

// Purchase orders
app.get('/api/purchase-orders', (_req, res) => res.json(purchaseOrders));

app.post('/api/purchase-orders', (req, res) => {
  const { supplier, items, total } = req.body;
  const po: PurchaseOrder = {
    id: `PO-${1044 + purchaseOrders.length}`,
    supplier: supplier || 'Unknown supplier',
    items: Number(items) || 1,
    total: Number(total) || 0,
    status: 'Awaiting delivery',
  };
  purchaseOrders.unshift(po);
  logAudit('Amina N.', `created purchase order ${po.id}`, `Supplier: ${po.supplier} · KSh ${po.total.toLocaleString()}`);
  res.status(201).json(po);
});

// Branches & Warehouses
app.get('/api/branches', (_req, res) => res.json(branches));

app.post('/api/branches', (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'name is required' });
  const b: Branch = { id: nextBranchId++, name, sales: 0, staff: 1, lowStock: 0 };
  branches.push(b);
  logAudit('Amina N.', `added branch ${b.name}`);
  res.status(201).json(b);
});

app.get('/api/warehouses', (_req, res) => res.json(warehouses));

app.post('/api/warehouses/transfer', (req, res) => {
  const { from, to, amount } = req.body;
  logAudit('Amina N.', `transferred stock from ${from || 'warehouse'} to ${to || 'branch'}`, `Value: KSh ${(amount || 0).toLocaleString()}`);
  res.json({ success: true });
});

// Expenses
app.get('/api/expenses', (_req, res) => res.json(expenses));

app.post('/api/expenses', (req, res) => {
  const { amount, category, method, description } = req.body;
  if (!amount) return res.status(400).json({ error: 'amount is required' });
  const expense: Expense = {
    id: nanoid(6),
    amount: Number(amount),
    category: category || 'Other',
    method: method || 'Cash',
    description: description || '',
    at: new Date().toISOString(),
  };
  expenses.unshift(expense);
  logAudit('Amina N.', `recorded ${expense.category} expense`, `KSh ${expense.amount.toLocaleString()}`);
  res.status(201).json(expense);
});

// eTIMS
app.get('/api/etims', (_req, res) => {
  const accepted = sales.filter((s) => s.etimsStatus === 'Accepted').length;
  const failed = sales.filter((s) => s.etimsStatus === 'Failed').length;
  const pending = sales.filter((s) => !s.etimsStatus || s.etimsStatus === 'Pending').length;
  res.json({
    connected: capabilities.etims,
    submittedToday: sales.length,
    accepted,
    failed,
    pending,
    logs: sales.slice(-20).reverse().map((s) => ({
      id: `#${s.id}`,
      status: s.etimsStatus || 'Pending',
      time: new Date(s.at).toLocaleTimeString('en-KE', { hour: 'numeric', minute: '2-digit' }),
    })),
  });
});

app.post('/api/etims/retry', (_req, res) => {
  for (const s of sales) {
    if (s.etimsStatus === 'Failed' || s.etimsStatus === 'Pending') {
      s.etimsStatus = 'Accepted';
    }
  }
  logAudit('Amina N.', 'manually retried pending/failed eTIMS submissions');
  res.json({ success: true });
});

// Sales (checkout + refunds)
app.get('/api/sales', (_req, res) => res.json([...sales].reverse().slice(0, 20)));

app.post('/api/sales', (req, res) => {
  const { items, method, customerId } = req.body as { items: SaleItem[]; method: string; customerId?: number };
  if (!items || !items.length) return res.status(400).json({ error: 'items are required' });

  for (const item of items) {
    const product = products.find((p) => p.id === item.productId);
    if (!product || product.stock < item.qty) {
      return res.status(409).json({ error: `Not enough stock for ${item.name}` });
    }
  }
  for (const item of items) {
    const product = products.find((p) => p.id === item.productId)!;
    product.stock -= item.qty;
    stockMovements.push({ id: nanoid(6), productId: product.id, type: 'sold', qty: -item.qty, note: 'Sale', at: new Date().toISOString() });
  }
  const total = items.reduce((sum, it) => sum + it.price * it.qty, 0);

  let custName: string | undefined;
  if (customerId) {
    const cust = customers.find((c) => c.id === customerId);
    if (cust) {
      custName = cust.name;
      cust.purchases += 1;
      cust.total += total;
      if (method === 'credit') {
        cust.balance += total;
      }
    }
  }

  const etimsStatus = capabilities.etims ? 'Accepted' : 'Pending';
  const sale: Sale = { id: nextSaleId++, items, total, method: method || 'cash', at: new Date().toISOString(), customerId, customerName: custName, etimsStatus };
  sales.push(sale);
  res.status(201).json(sale);
});

app.post('/api/sales/:id/refund', (req, res) => {
  const id = Number(req.params.id);
  const sale = sales.find((s) => s.id === id);
  if (!sale) return res.status(404).json({ error: 'Sale not found' });
  const indices: number[] = req.body.itemIndices || [];
  for (const i of indices) {
    const item = sale.items[i];
    if (!item) continue;
    const product = products.find((p) => p.id === item.productId);
    if (product) {
      product.stock += item.qty;
      stockMovements.push({ id: nanoid(6), productId: product.id, type: 'refunded', qty: item.qty, note: req.body.reason || 'Refund', at: new Date().toISOString() });
    }
  }
  sale.refunded = true;
  logAudit('Amina N.', `refunded sale #${sale.id}`, `KSh ${sale.total.toLocaleString()}`);
  res.json(sale);
});

// Capabilities
app.get('/api/capabilities', (_req, res) => res.json(capabilities));

app.post('/api/capabilities/:key/unlock', (req, res) => {
  const key = req.params.key as CapabilityKey;
  if (!(key in capabilities)) return res.status(400).json({ error: 'Unknown capability' });
  capabilities[key] = true;
  logAudit('Amina N.', `unlocked the ${key} capability`);
  res.json(capabilities);
});

// Team
app.get('/api/team', (_req, res) => res.json(team));

app.post('/api/team', (req, res) => {
  const { name, phone, role } = req.body;
  if (!name) return res.status(400).json({ error: 'name is required' });
  const member: TeamMember = { id: nanoid(6), name, phone: phone || '', role: role || 'Cashier', status: 'Active' };
  team.push(member);
  logAudit('Amina N.', `added ${name} to the team as ${member.role}`);
  res.status(201).json(member);
});

// Audit log
app.get('/api/audit', (_req, res) => res.json(auditLog.slice(0, 30)));

// Settings
app.get('/api/settings', (_req, res) => res.json(settings));

app.put('/api/settings/:section', (req, res) => {
  const section = req.params.section as keyof Settings;
  if (!(section in settings)) return res.status(400).json({ error: 'Unknown settings section' });
  settings[section] = { ...settings[section], ...req.body } as never;
  res.json(settings[section]);
});

// Dashboard — computed metrics
app.get('/api/dashboard', (_req, res) => {
  const todaySales = sales.filter((s) => !s.refunded);
  const totalSales = todaySales.reduce((sum, s) => sum + s.total, 0);
  const byMethod = { cash: 0, mpesa: 0, card: 0, split: 0, credit: 0 };
  for (const s of todaySales) {
    const m = s.method as keyof typeof byMethod;
    if (m in byMethod) byMethod[m] += s.total;
  }
  const itemsSold = todaySales.reduce((sum, s) => sum + s.items.reduce((a, it) => a + it.qty, 0), 0);
  const grossProfit = todaySales.reduce((sum, s) => {
    return (
      sum +
      s.items.reduce((a, it) => {
        const product = products.find((p) => p.id === it.productId);
        return a + (it.price - (product?.cost ?? it.price * 0.85)) * it.qty;
      }, 0)
    );
  }, 0);
  const refundedSales = sales.filter((s) => s.refunded);
  const refundedTotal = refundedSales.reduce((sum, s) => sum + s.total, 0);
  const lowStock = products.filter((p) => p.stock <= p.reorder);

  const alerts: { kind: string; title: string; sub: string }[] = [];
  if (lowStock.length > 0) {
    alerts.push({
      kind: 'warn',
      title: `${lowStock.length} product${lowStock.length > 1 ? 's are' : ' is'} low on stock`,
      sub: lowStock.slice(0, 3).map((p) => p.name).join(', '),
    });
  }
  if (refundedSales.length > 0) {
    alerts.push({ kind: 'neutral', title: `${refundedSales.length} refund${refundedSales.length > 1 ? 's' : ''} today`, sub: `Total KSh ${refundedTotal.toLocaleString()} refunded` });
  }
  if (!capabilities.etims) {
    alerts.push({ kind: 'neutral', title: 'eTIMS isn\'t connected', sub: 'Connect it from the eTIMS page to automate tax submission' });
  }

  res.json({
    totalSales,
    transactions: todaySales.length,
    itemsSold,
    grossProfit: Math.round(grossProfit),
    cash: byMethod.cash,
    mpesa: byMethod.mpesa,
    card: byMethod.card,
    lowStockCount: lowStock.length,
    refundedTotal,
    alerts,
    trend: [18, 22, 16, 27, 24, 31, Math.max(1, Math.round(totalSales / 1000))],
  });
});

// Reports — computed breakdowns by period
app.get('/api/reports', (req, res) => {
  const period = (req.query.period as string) || 'Today';
  const now = Date.now();
  let msLimit = 24 * 3600_000;
  if (period === 'This week') msLimit = 7 * 24 * 3600_000;
  if (period === 'This month') msLimit = 30 * 24 * 3600_000;
  if (period === 'Custom' || period === 'All') msLimit = 365 * 24 * 3600_000;

  const live = sales.filter((s) => !s.refunded && (now - new Date(s.at).getTime()) <= msLimit);
  const totalSales = live.reduce((sum, s) => sum + s.total, 0);
  const byMethod: Record<string, number> = {};
  for (const s of live) byMethod[s.method] = (byMethod[s.method] || 0) + s.total;

  const byProduct: Record<string, number> = {};
  for (const s of live) for (const it of s.items) byProduct[it.name] = (byProduct[it.name] || 0) + it.price * it.qty;
  const topProducts = Object.entries(byProduct)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([name, val]) => ({ name, val }));

  const itemsSold = live.reduce((sum, s) => sum + s.items.reduce((a, it) => a + it.qty, 0), 0);

  res.json({
    totalSales,
    transactions: live.length,
    itemsSold,
    avgTransaction: live.length ? Math.round(totalSales / live.length) : 0,
    byMethod: Object.entries(byMethod).map(([label, val]) => ({ label, val })),
    topProducts,
  });
});

const PORT = process.env.PORT ? Number(process.env.PORT) : 4000;
app.listen(PORT, () => {
  console.log(`Duka API listening on http://localhost:${PORT}`);
});

