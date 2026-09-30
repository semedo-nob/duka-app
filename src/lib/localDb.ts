import { backoffMs, commitCheckout, type LedgerMovement, type LedgerSale } from './posLedger';

const DB_NAME = 'duka-pos';
const VERSION = 3;

export type LocalSaleBody = {
  items: { productId: number; name: string; qty: number; price: number }[];
  method: string;
  customerId?: number;
  discount?: number;
  tendered?: number;
  payments?: { method: string; amount: number }[];
};

export type OutboxEntry = {
  idempotencyKey: string;
  kind: 'sale';
  body: LocalSaleBody;
  createdAt: string;
  attempts: number;
  lastError?: string;
  status: 'pending' | 'synced' | 'failed';
  serverSaleId?: number;
  nextAttemptAt?: string;
  clientSaleNo?: string;
};

type ProductRow = {
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
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains('products')) db.createObjectStore('products', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('outbox')) db.createObjectStore('outbox', { keyPath: 'idempotencyKey' });
      if (!db.objectStoreNames.contains('sales')) db.createObjectStore('sales', { keyPath: 'idempotencyKey' });
      if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'key' });
      if (!db.objectStoreNames.contains('movements')) db.createObjectStore('movements', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('staff')) db.createObjectStore('staff', { keyPath: 'phone' });
      if (!db.objectStoreNames.contains('customers')) db.createObjectStore('customers', { keyPath: 'id' });
    };
    request.onsuccess = () => {
      const db = request.result;
      db.onversionchange = () => db.close();
      resolve(db);
    };
    request.onerror = () => reject(request.error);
    request.onblocked = () => reject(new Error('Local database upgrade is blocked. Reload Duka.'));
  });
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function saveProducts(products: ProductRow[]) {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('products', 'readwrite');
    tx.objectStore('products').clear();
    for (const product of products) tx.objectStore('products').put(product);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function readProducts(): Promise<ProductRow[]> {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('products').objectStore('products').getAll() as IDBRequest<ProductRow[]>);
  db.close();
  return rows;
}

export async function recordLocalSale(idempotencyKey: string, body: LocalSaleBody) {
  const db = await openDb();
  const entry: OutboxEntry = {
    idempotencyKey,
    kind: 'sale',
    body,
    createdAt: new Date().toISOString(),
    attempts: 0,
    status: 'pending',
  };
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(['sales', 'outbox'], 'readwrite');
    tx.objectStore('sales').put({ ...entry });
    tx.objectStore('outbox').put(entry);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function listPending(): Promise<OutboxEntry[]> {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('outbox').objectStore('outbox').getAll() as IDBRequest<OutboxEntry[]>);
  db.close();
  return rows.filter((row) => row.status === 'pending');
}

export async function pendingCount(): Promise<number> {
  return (await listPending()).length;
}

export async function markSynced(idempotencyKey: string, serverSaleId: number) {
  await updateOutbox(idempotencyKey, (row) => ({ ...row, status: 'synced', serverSaleId, lastError: undefined }));
}

export async function markFailed(idempotencyKey: string, lastError: string) {
  await updateOutbox(idempotencyKey, (row) => ({ ...row, status: 'failed', attempts: row.attempts + 1, lastError }));
}

export async function bumpAttempt(idempotencyKey: string, lastError: string) {
  await updateOutbox(idempotencyKey, (row) => {
    const attempts = row.attempts + 1;
    return { ...row, attempts, lastError, nextAttemptAt: new Date(Date.now() + backoffMs(attempts)).toISOString() };
  });
}

export async function requeueFailed() {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('outbox').objectStore('outbox').getAll() as IDBRequest<OutboxEntry[]>);
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('outbox', 'readwrite');
    for (const row of rows) {
      if (row.status === 'failed') tx.objectStore('outbox').put({ ...row, status: 'pending', nextAttemptAt: undefined });
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function listDue(now = Date.now()): Promise<OutboxEntry[]> {
  const rows = await listPending();
  return rows.filter((row) => !row.nextAttemptAt || Date.parse(row.nextAttemptAt) <= now);
}

export async function countByStatus(status: OutboxEntry['status']) {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('outbox').objectStore('outbox').getAll() as IDBRequest<OutboxEntry[]>);
  db.close();
  return rows.filter((row) => row.status === status).length;
}

export type DeviceIdentity = { deviceId: string; installationId: string; businessId: string; sequence: number };

export async function ensureIdentity(): Promise<DeviceIdentity> {
  const db = await openDb();
  const identity = await new Promise<DeviceIdentity>((resolve, reject) => {
    const tx = db.transaction('meta', 'readwrite');
    const store = tx.objectStore('meta');
    const get = store.get('identity');
    get.onsuccess = () => {
      const current = get.result as { key: string; value: DeviceIdentity } | undefined;
      if (current?.value?.deviceId) {
        resolve(current.value);
        return;
      }
      const value: DeviceIdentity = {
        deviceId: crypto.randomUUID(),
        installationId: crypto.randomUUID(),
        businessId: 'unassigned',
        sequence: 0,
      };
      store.put({ key: 'identity', value });
      resolve(value);
    };
    tx.onerror = () => reject(tx.error);
  });
  db.close();
  return identity;
}

export async function rememberBusiness(businessId: string) {
  const identity = await ensureIdentity();
  if (identity.businessId === businessId) return identity;
  const next = { ...identity, businessId };
  await putMeta('identity', next);
  return next;
}

async function putMeta(key: string, value: unknown) {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('meta', 'readwrite');
    tx.objectStore('meta').put({ key, value });
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function readMeta<T>(key: string): Promise<T | undefined> {
  const db = await openDb();
  const row = await requestToPromise(db.transaction('meta').objectStore('meta').get(key) as IDBRequest<{ value: T } | undefined>);
  db.close();
  return row?.value;
}

export async function writeMeta(key: string, value: unknown) {
  await putMeta(key, value);
}

export async function unsyncedProductIds(): Promise<Set<number>> {
  const db = await openDb();
  const sales = await requestToPromise(db.transaction('sales').objectStore('sales').getAll() as IDBRequest<Array<LedgerSale & { syncStatus?: string }>>);
  const outbox = await requestToPromise(db.transaction('outbox').objectStore('outbox').getAll() as IDBRequest<OutboxEntry[]>);
  db.close();
  const pending = new Set(outbox.filter((row) => row.status === 'pending').map((row) => row.idempotencyKey));
  const ids = new Set<number>();
  for (const sale of sales) {
    if (!sale.stockApplied || !pending.has(sale.idempotencyKey)) continue;
    for (const item of sale.items || []) ids.add(item.productId);
  }
  return ids;
}

export async function commitLocalCheckout(input: {
  idempotencyKey: string;
  method: string;
  items: { productId: number; name: string; qty: number; price: number }[];
  discount: number;
  tax: number;
  subtotal: number;
  total: number;
  customerId?: number;
  tendered?: number;
  payments?: { method: string; amount: number }[];
  cashierName?: string;
}): Promise<LedgerSale> {
  const identity = await ensureIdentity();
  const db = await openDb();
  const sale = await new Promise<LedgerSale>((resolve, reject) => {
    const tx = db.transaction(['products', 'sales', 'movements', 'outbox', 'meta'], 'readwrite');
    const productsStore = tx.objectStore('products');
    const salesStore = tx.objectStore('sales');
    const productsReq = productsStore.getAll();
    const saleReq = salesStore.get(input.idempotencyKey);
    const identityReq = tx.objectStore('meta').get('identity');
    let products: ProductRow[] = [];
    let existing: LedgerSale | undefined;
    let sequence = identity.sequence;
    let deviceId = identity.deviceId;
    let installationId = identity.installationId;
    let businessId = identity.businessId;
    productsReq.onsuccess = () => {
      products = productsReq.result as ProductRow[];
    };
    saleReq.onsuccess = () => {
      const row = saleReq.result as LedgerSale | undefined;
      if (row?.clientSaleNo) existing = row;
    };
    identityReq.onsuccess = () => {
      const row = identityReq.result as { value: DeviceIdentity } | undefined;
      if (row?.value) {
        sequence = row.value.sequence;
        deviceId = row.value.deviceId;
        installationId = row.value.installationId;
        businessId = row.value.businessId;
      }
    };
    tx.oncomplete = () => {
      try {
        const result = commitCheckout(products, existing, { ...input, deviceId, installationId, businessId, sequence });
        const write = db.transaction(['products', 'sales', 'movements', 'outbox', 'meta'], 'readwrite');
        for (const product of result.products) write.objectStore('products').put(product);
        write.objectStore('sales').put(result.sale);
        for (const movement of result.movements) write.objectStore('movements').put(movement satisfies LedgerMovement);
        const outbox: OutboxEntry = {
          idempotencyKey: result.sale.idempotencyKey,
          kind: 'sale',
          body: {
            items: input.items,
            method: input.method,
            customerId: input.customerId,
            discount: input.discount,
            tendered: input.tendered,
            payments: input.payments,
          },
          createdAt: result.sale.createdAt,
          attempts: 0,
          status: 'pending',
          clientSaleNo: result.sale.clientSaleNo,
        };
        if (!existing) {
          write.objectStore('outbox').put(outbox);
          write.objectStore('meta').put({
            key: 'identity',
            value: { deviceId, installationId, businessId, sequence: result.sale.sequence },
          });
        }
        write.oncomplete = () => resolve(result.sale);
        write.onerror = () => reject(write.error);
      } catch (err) {
        reject(err);
      }
    };
    tx.onerror = () => reject(tx.error);
  });
  db.close();
  return sale;
}

export async function readLocalSale(idempotencyKey: string): Promise<LedgerSale | undefined> {
  const db = await openDb();
  const row = await requestToPromise(db.transaction('sales').objectStore('sales').get(idempotencyKey) as IDBRequest<LedgerSale | undefined>);
  db.close();
  return row?.clientSaleNo ? row : undefined;
}

export type StaffRecord = {
  phone: string;
  userId: number;
  name: string;
  role: string;
  salt: string;
  verifier: string;
};

export async function saveStaff(record: StaffRecord) {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('staff', 'readwrite');
    tx.objectStore('staff').put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function readStaff(phone: string): Promise<StaffRecord | undefined> {
  const db = await openDb();
  const row = await requestToPromise(db.transaction('staff').objectStore('staff').get(phone.replace(/\s+/g, '')) as IDBRequest<StaffRecord | undefined>);
  db.close();
  return row;
}

export async function saveCustomers(customers: { id: number; name: string; phone: string; purchases: number; total: number; balance: number }[]) {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('customers', 'readwrite');
    tx.objectStore('customers').clear();
    for (const customer of customers) tx.objectStore('customers').put(customer);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

export async function readCustomers() {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('customers').objectStore('customers').getAll());
  db.close();
  return rows as { id: number; name: string; phone: string; purchases: number; total: number; balance: number }[];
}

export async function recentLocalSales(): Promise<LedgerSale[]> {
  const db = await openDb();
  const rows = await requestToPromise(db.transaction('sales').objectStore('sales').getAll() as IDBRequest<LedgerSale[]>);
  db.close();
  return rows.filter((row) => row.clientSaleNo).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 20);
}

async function updateOutbox(idempotencyKey: string, change: (row: OutboxEntry) => OutboxEntry) {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(['outbox', 'sales'], 'readwrite');
    const store = tx.objectStore('outbox');
    const get = store.get(idempotencyKey);
    get.onsuccess = () => {
      const row = get.result as OutboxEntry | undefined;
      if (!row) return;
      const next = change(row);
      store.put(next);
      const sales = tx.objectStore('sales');
      const saleGet = sales.get(idempotencyKey);
      saleGet.onsuccess = () => {
        const sale = saleGet.result as (LedgerSale & { serverSaleId?: number; syncStatus?: string }) | undefined;
        if (!sale || !sale.clientSaleNo) return;
        sales.put({ ...sale, serverSaleId: next.serverSaleId, syncStatus: next.status });
      };
    };
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}
