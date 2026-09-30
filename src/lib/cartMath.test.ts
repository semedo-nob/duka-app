import { describe, expect, it } from 'vitest';
import { cartTotals } from './cartMath';
import { createBarcodeWedge } from './barcodeBuffer';

describe('cart totals', () => {
  it('treats shelf prices as VAT-inclusive and applies a discount', () => {
    const totals = cartTotals([{ price: 116, qty: 2 }], 16);
    expect(totals.gross).toBe(232);
    expect(totals.total).toBe(216);
    expect(totals.tax).toBe(30);
    expect(totals.subtotal).toBe(186);
  });

  it('does not let a discount exceed the sale', () => {
    const totals = cartTotals([{ price: 50, qty: 1 }], 80);
    expect(totals.total).toBe(0);
    expect(totals.discount).toBe(50);
  });
});

describe('barcode wedge', () => {
  it('accepts a fast scan and ignores slow typing', () => {
    const seen: string[] = [];
    const onKey = createBarcodeWedge((code) => seen.push(code), 40);
    const now = Date.now();
    const realNow = Date.now;
    let clock = now;
    Date.now = () => clock;
    try {
      for (const key of ['6', '1', '6', '1']) {
        clock += 10;
        onKey({ key, ctrlKey: false, metaKey: false, altKey: false, preventDefault() {}, stopPropagation() {} } as KeyboardEvent);
      }
      clock += 10;
      onKey({ key: 'Enter', ctrlKey: false, metaKey: false, altKey: false, preventDefault() {}, stopPropagation() {} } as KeyboardEvent);
      expect(seen).toEqual(['6161']);

      clock += 200;
      for (const key of ['m', 'i', 'l', 'k']) {
        clock += 80;
        onKey({ key, ctrlKey: false, metaKey: false, altKey: false, preventDefault() {}, stopPropagation() {} } as KeyboardEvent);
      }
      onKey({ key: 'Enter', ctrlKey: false, metaKey: false, altKey: false, preventDefault() {}, stopPropagation() {} } as KeyboardEvent);
      expect(seen).toEqual(['6161']);
    } finally {
      Date.now = realNow;
    }
  });
});
