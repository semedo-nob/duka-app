import { describe, expect, it } from 'vitest';
import { awaitsProvider, backoffMs, commitCheckout, mergeCatalogStock } from './posLedger';
import { receiptText } from './receipt';
import { createBarcodeWedge } from './barcodeBuffer';

const identity = {
  idempotencyKey: 'key-1',
  deviceId: 'abcd-device',
  installationId: 'inst-1',
  businessId: 'biz-1',
  sequence: 4,
  method: 'cash',
  items: [{ productId: 1, name: 'Milk', qty: 2, price: 65 }],
  discount: 0,
  tax: 18,
  subtotal: 112,
  total: 130,
  now: '2026-09-29T08:00:00.000Z',
};

describe('local sale ledger', () => {
  it('commits a cash sale once and reduces stock', () => {
    const products = [{ id: 1, name: 'Milk', stock: 5, active: true }];
    const first = commitCheckout(products, undefined, identity);
    expect(first.sale.clientSaleNo).toBe('ABCD-5');
    expect(first.sale.stockApplied).toBe(true);
    expect(first.products[0].stock).toBe(3);
    expect(first.movements).toHaveLength(1);
    expect(first.movements[0].qty).toBe(-2);
    const again = commitCheckout(first.products, first.sale, identity);
    expect(again.products[0].stock).toBe(3);
    expect(again.movements).toHaveLength(0);
  });

  it('refuses to sell more than the device has', () => {
    expect(() => commitCheckout([{ id: 1, name: 'Milk', stock: 1 }], undefined, identity)).toThrow(/enough stock/);
  });

  it('holds M-Pesa without moving stock', () => {
    const result = commitCheckout([{ id: 1, name: 'Milk', stock: 5 }], undefined, { ...identity, method: 'mpesa' });
    expect(result.sale.status).toBe('AWAITING_PAYMENT');
    expect(result.sale.stockApplied).toBe(false);
    expect(result.products[0].stock).toBe(5);
    expect(awaitsProvider('split', [{ method: 'cash', amount: 30 }, { method: 'mpesa', amount: 100 }])).toBe(true);
  });

  it('keeps unsynced local quantities when the catalogue refreshes', () => {
    const merged = mergeCatalogStock(
      [{ id: 1, stock: 10 }, { id: 2, stock: 4 }],
      [{ id: 1, stock: 3 }, { id: 2, stock: 4 }],
      new Set([1]),
    );
    expect(merged.map((row) => row.stock)).toEqual([3, 4]);
  });

  it('backs off and then caps the wait', () => {
    expect(backoffMs(0)).toBe(15_000);
    expect(backoffMs(2)).toBe(60_000);
    expect(backoffMs(20)).toBe(15 * 60 * 1000);
  });
});

describe('receipt', () => {
  it('includes the business, items, tax, and reprint mark', () => {
    const { sale } = commitCheckout([{ id: 1, name: 'Milk', stock: 5 }], undefined, identity);
    const text = receiptText({ businessName: 'Mama Njeri Store', address: 'Ngong Road', sale, reprint: true });
    expect(text).toContain('Mama Njeri Store');
    expect(text).toContain('Milk');
    expect(text).toContain('TOTAL 130.00');
    expect(text).toContain('REPRINT');
    expect(text).toContain('VAT included');
  });
});

describe('barcode wedge', () => {
  it('accepts a fast scan ending in Enter and ignores slow typing', () => {
    const seen: string[] = [];
    const onKey = createBarcodeWedge((code) => seen.push(code), 5000);
    const fire = (key: string) => onKey({ key, ctrlKey: false, metaKey: false, altKey: false, preventDefault() {}, stopPropagation() {} } as KeyboardEvent);
    for (const key of '6161100000001') fire(key);
    fire('Enter');
    expect(seen).toEqual(['6161100000001']);
  });
});
