export type PricedLine = { price: number; qty: number };

/** Shelf prices include VAT. Tax is the included portion, not an amount added on top. */
export function cartTotals(lines: PricedLine[], discount = 0, taxRate = 0.16) {
  const gross = lines.reduce((sum, line) => sum + line.price * line.qty, 0);
  const safeDiscount = Math.min(Math.max(0, discount), Math.max(0, gross));
  const total = Math.max(0, gross - safeDiscount);
  const tax = Math.round((total * taxRate) / (1 + taxRate));
  return { gross, discount: safeDiscount, subtotal: total - tax, tax, total };
}
