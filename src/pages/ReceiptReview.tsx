import { useState } from 'react';
import { useProducts, useReceiptReviews, useUploadReceipt, useUpdateReceipt, useApproveReceipt, useRejectReceipt } from '../lib/queries';
import { useToast } from '../components/Toast';
import { money } from '../lib/format';
import type { Extraction, ExtractionLine } from '../lib/api';

export default function ReceiptReview() {
  const { data: reviews, isLoading, isError } = useReceiptReviews();
  const { data: products } = useProducts();
  const upload = useUploadReceipt();
  const update = useUpdateReceipt();
  const approve = useApproveReceipt();
  const reject = useRejectReceipt();
  const showToast = useToast();
  const [supplierName, setSupplierName] = useState('');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [active, setActive] = useState<Extraction | null>(null);

  const open = reviews?.filter((review) => review.status === 'REVIEW') ?? [];

  function saveLine(review: Extraction, line: ExtractionLine, patch: Partial<ExtractionLine>) {
    const next = { ...line, ...patch };
    setActive({ ...review, lines: review.lines.map((row) => (row.id === line.id ? next : row)) });
    update.mutate(
      {
        id: review.id,
        data: {
          supplierName: review.supplierName,
          invoiceNumber: review.invoiceNumber,
          lines: [{ id: line.id, rawName: next.rawName, quantity: next.quantity, unitCost: next.unitCost, matchedProductId: next.matchedProductId, removed: next.removed }],
        },
      },
      { onError: (err) => showToast(err instanceof Error ? err.message : 'Could not save the line') },
    );
  }

  return (
    <>
      <div className="card panel" style={{ marginBottom: 18 }}>
        <h3 style={{ marginTop: 0 }}>Import a supplier invoice</h3>
        <p style={{ fontSize: 13, color: 'var(--ink-soft)', marginTop: 0 }}>
          Upload a CSV, PDF, or photo. Extracted lines stay in review until you approve them. Stock does not change on upload.
        </p>
        <div className="form-row2">
          <div className="form-field">
            <label>Supplier</label>
            <input value={supplierName} onChange={(e) => setSupplierName(e.target.value)} placeholder="ABC Wholesalers" />
          </div>
          <div className="form-field">
            <label>Receipt / invoice number</label>
            <input value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} placeholder="INV-20491" />
          </div>
        </div>
        <input
          type="file"
          accept=".csv,.txt,.json,.pdf,image/*"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            upload.mutate(
              { file, supplierName, invoiceNumber },
              {
                onSuccess: (review) => {
                  setActive(review);
                  showToast('Document stored for review');
                },
                onError: (err) => showToast(err instanceof Error ? err.message : 'Upload failed'),
              },
            );
            e.target.value = '';
          }}
        />
      </div>

      {isLoading && <p>Loading reviews…</p>}
      {isError && <p>Couldn't reach the server.</p>}

      {(active ? [active] : open).map((review) => (
        <div className="card" key={review.id} style={{ marginBottom: 16, padding: 16 }}>
          <h3 style={{ marginTop: 0 }}>Review stock receipt</h3>
          <p style={{ marginTop: 0 }}>
            Supplier: <b>{review.supplierName || '—'}</b>
            <br />
            Receipt: <b>{review.invoiceNumber || '—'}</b>
          </p>
          {review.notes && (
            <p style={{ fontSize: 12.5, color: 'var(--ink-soft)', whiteSpace: 'pre-wrap' }}>{review.notes}</p>
          )}
          <table>
            <thead>
              <tr>
                <th>Extracted item</th>
                <th>Matched product</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {review.lines.filter((line) => !line.removed).map((line) => (
                <tr key={line.id}>
                  <td>
                    <input value={line.rawName} onChange={(e) => saveLine(review, line, { rawName: e.target.value })} />
                    <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                      <input type="number" value={line.quantity} onChange={(e) => saveLine(review, line, { quantity: Number(e.target.value) })} />
                      <input type="number" value={line.unitCost} onChange={(e) => saveLine(review, line, { unitCost: Number(e.target.value) })} />
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--ink-faint)', marginTop: 4 }}>
                      Qty {line.quantity} · Cost {money(Number(line.unitCost))}
                      {line.matchMethod ? ` · ${line.matchMethod}` : ''}
                    </div>
                  </td>
                  <td>
                    <select
                      value={line.matchedProductId ?? ''}
                      onChange={(e) => saveLine(review, line, { matchedProductId: e.target.value ? Number(e.target.value) : null })}
                    >
                      <option value="">Select product</option>
                      {(products || []).map((product) => (
                        <option key={product.id} value={product.id}>
                          {product.name}
                        </option>
                      ))}
                    </select>
                    {line.suggestedProductName && !line.matchedProductId && (
                      <button className="btn btn-ghost btn-sm" style={{ marginTop: 6 }} onClick={() => saveLine(review, line, { matchedProductId: line.suggestedProductId })}>
                        Use suggestion: {line.suggestedProductName}
                      </button>
                    )}
                    {line.matchedProductName && <div style={{ fontSize: 12, marginTop: 4 }}>{line.matchedProductName}</div>}
                  </td>
                  <td>
                    <button className="btn btn-ghost btn-sm" onClick={() => saveLine(review, line, { removed: true })}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 14 }}>
            <button
              className="btn btn-ghost"
              disabled={reject.isPending}
              onClick={() =>
                reject.mutate(review.id, {
                  onSuccess: () => {
                    setActive(null);
                    showToast('Document rejected. Stock was not changed.');
                  },
                })
              }
            >
              Reject
            </button>
            <button
              className="btn btn-primary"
              disabled={approve.isPending}
              onClick={() =>
                approve.mutate(review.id, {
                  onSuccess: () => {
                    setActive(null);
                    showToast('Receipt approved and stock updated');
                  },
                  onError: (err) => showToast(err instanceof Error ? err.message : 'Could not approve'),
                })
              }
            >
              Approve
            </button>
          </div>
        </div>
      ))}
      {!isLoading && open.length === 0 && !active && (
        <div className="empty">
          <h3>No documents waiting</h3>
          <p>Imported invoices show up here until someone approves or rejects them.</p>
        </div>
      )}
    </>
  );
}
