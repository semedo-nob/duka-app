export type LedgerItem = { productId: number; name: string; qty: number; price: number };

export type LedgerSale = {
  idempotencyKey: string;
  clientSaleNo: string;
  deviceId: string;
  installationId: string;
  businessId: string;
  createdAt: string;
  method: string;
  items: LedgerItem[];
  discount: number;
  tax: number;
  subtotal: number;
  total: number;
  status: 'COMPLETED' | 'AWAITING_PAYMENT';
  stockApplied: boolean;
  customerId?: number;
  tendered?: number;
  payments?: { method: string; amount: number }[];
  cashierName?: string;
  sequence: number;
};

export type LedgerMovement = {
  id: string;
  productId: number;
  type: 'SALE';
  qty: number;
  saleKey: string;
  at: string;
  note: string;
};

export type StockRow = { id: number; name: string; stock: number; active?: boolean };

export class LocalSaleError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LocalSaleError';
  }
}

export function awaitsProvider(method: string, payments?: { method: string; amount: number }[]) {
  if (method === 'mpesa') return true;
  if (method === 'split') return (payments || []).some((part) => part.method === 'mpesa');
  return false;
}

export function backoffMs(attempts: number) {
  const delay = 15_000 * 2 ** Math.min(Math.max(0, attempts), 8);
  return Math.min(delay, 15 * 60 * 1000);
}

export function commitCheckout<T extends StockRow>(
  products: T[],
  existing: LedgerSale | undefined,
  input: {
    idempotencyKey: string;
    deviceId: string;
    installationId: string;
    businessId: string;
    sequence: number;
    method: string;
    items: LedgerItem[];
    discount: number;
    tax: number;
    subtotal: number;
    total: number;
    customerId?: number;
    tendered?: number;
    payments?: { method: string; amount: number }[];
    cashierName?: string;
    now?: string;
  },
): { products: T[]; sale: LedgerSale; movements: LedgerMovement[] } {
  if (existing) {
    return { products, sale: existing, movements: [] };
  }
  if (!input.items.length) throw new LocalSaleError('Add at least one item');
  if (input.total < 0) throw new LocalSaleError('Total cannot be negative');
  const pending = awaitsProvider(input.method, input.payments);
  const nextProducts = products.map((product) => ({ ...product }));
  const movements: LedgerMovement[] = [];
  if (!pending) {
    for (const item of input.items) {
      if (item.qty <= 0) throw new LocalSaleError('Quantity must be positive');
      const product = nextProducts.find((row) => row.id === item.productId);
      if (!product) throw new LocalSaleError('Product is not on this device');
      if (product.active === false) throw new LocalSaleError(`${product.name} is inactive`);
      if (product.stock < item.qty) throw new LocalSaleError(`${product.name} does not have enough stock on this device`);
    }
    for (const item of input.items) {
      const product = nextProducts.find((row) => row.id === item.productId)!;
      product.stock -= item.qty;
      movements.push({
        id: `${input.idempotencyKey}:${item.productId}`,
        productId: item.productId,
        type: 'SALE',
        qty: -item.qty,
        saleKey: input.idempotencyKey,
        at: input.now || new Date().toISOString(),
        note: 'Sale',
      });
    }
  }
  const sequence = input.sequence + 1;
  const sale: LedgerSale = {
    idempotencyKey: input.idempotencyKey,
    clientSaleNo: `${input.deviceId.slice(0, 4).toUpperCase()}-${sequence}`,
    deviceId: input.deviceId,
    installationId: input.installationId,
    businessId: input.businessId,
    createdAt: input.now || new Date().toISOString(),
    method: input.method,
    items: input.items,
    discount: input.discount,
    tax: input.tax,
    subtotal: input.subtotal,
    total: input.total,
    status: pending ? 'AWAITING_PAYMENT' : 'COMPLETED',
    stockApplied: !pending,
    customerId: input.customerId,
    tendered: input.tendered,
    payments: input.payments,
    cashierName: input.cashierName,
    sequence,
  };
  return { products: nextProducts, sale, movements };
}

export function mergeCatalogStock<T extends { id: number; stock: number }>(server: T[], local: T[], unsyncedProductIds: Set<number>): T[] {
  const localById = new Map(local.map((row) => [row.id, row]));
  return server.map((row) => {
    const kept = localById.get(row.id);
    if (kept && unsyncedProductIds.has(row.id)) return { ...row, stock: kept.stock };
    return row;
  });
}
