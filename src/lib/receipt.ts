import type { LedgerSale } from './posLedger';

export type ReceiptCopy = {
  businessName: string;
  address?: string;
  phone?: string;
  sale: LedgerSale;
  reprint?: boolean;
};

export function receiptText(copy: ReceiptCopy) {
  const lines = [
    copy.businessName || 'Duka',
    copy.address || '',
    copy.phone || '',
    copy.reprint ? '*** REPRINT ***' : '',
    `Receipt ${copy.sale.clientSaleNo}`,
    copy.sale.createdAt,
    copy.sale.cashierName ? `Cashier ${copy.sale.cashierName}` : '',
    `Device ${copy.sale.deviceId}`,
    '--------------------------------',
  ];
  for (const item of copy.sale.items) {
    lines.push(`${item.name}`);
    lines.push(`  ${item.qty} x ${item.price.toFixed(2)} = ${(item.qty * item.price).toFixed(2)}`);
  }
  lines.push('--------------------------------');
  lines.push(`Discount ${copy.sale.discount.toFixed(2)}`);
  lines.push(`VAT included ${copy.sale.tax.toFixed(2)}`);
  lines.push(`TOTAL ${copy.sale.total.toFixed(2)}`);
  lines.push(`Payment ${copy.sale.method}`);
  if (copy.sale.status === 'AWAITING_PAYMENT') lines.push('M-Pesa NOT confirmed');
  lines.push(`Ref ${copy.sale.idempotencyKey}`);
  return lines.filter((line) => line !== '').join('\n');
}

type DesktopBridge = {
  printReceipt?: (text: string) => Promise<{ ok: boolean; detail: string }>;
};

export async function printReceipt(copy: ReceiptCopy): Promise<{ status: 'printed' | 'dialog' | 'failed'; detail: string }> {
  const text = receiptText(copy);
  const desktop = (window as Window & { dukaDesktop?: DesktopBridge }).dukaDesktop;
  if (desktop?.printReceipt) {
    try {
      const result = await desktop.printReceipt(text);
      return result.ok ? { status: 'printed', detail: result.detail } : { status: 'failed', detail: result.detail };
    } catch (err) {
      return { status: 'failed', detail: err instanceof Error ? err.message : 'Printer failed' };
    }
  }
  const popup = window.open('', 'duka-receipt', 'width=420,height=720');
  if (!popup) return { status: 'failed', detail: 'The browser blocked the receipt window' };
  popup.document.write(`<!doctype html><title>Receipt ${copy.sale.clientSaleNo}</title>
    <style>
      body { font: 13px/1.35 ui-monospace, monospace; width: 72mm; margin: 8px auto; white-space: pre-wrap; }
      @media print { body { width: 72mm; } }
    </style><pre>${text.replace(/[&<>]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[ch] || ch))}</pre>`);
  popup.document.close();
  popup.focus();
  popup.print();
  return { status: 'dialog', detail: 'Opened the system print dialog. This does not confirm that a printer finished the page.' };
}
