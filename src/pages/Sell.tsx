import { useEffect, useMemo, useRef, useState } from 'react';
import { Icons } from '../components/Icons';
import { PaymentOverlay } from '../components/pos/PaymentOverlay';
import { ShiftDrawer } from '../components/pos/ShiftDrawer';
import { RefundDrawer } from '../components/pos/RefundDrawer';
import { useStore } from '../store/useStore';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useProducts } from '../lib/queries';
import { api, type Product } from '../lib/api';
import { readProducts } from '../lib/localDb';
import { cartTotals } from '../lib/cartMath';
import { createBarcodeWedge } from '../lib/barcodeBuffer';

function shiftDuration(openedAt?: string) {
  const started = Date.parse(openedAt || '');
  if (!Number.isFinite(started)) return 'Shift open';
  const mins = Math.max(0, Math.floor((Date.now() - started) / 60000));
  return `Shift open · ${Math.floor(mins / 60)}h ${mins % 60}m`;
}

export default function Sell() {
  const [search, setSearch] = useState('');
  const [cat, setCat] = useState('All');
  const [showPayment, setShowPayment] = useState(false);
  const [showShift, setShowShift] = useState(false);
  const [showRefund, setShowRefund] = useState(false);
  const [barcode, setBarcode] = useState('');
  const [unknownCode, setUnknownCode] = useState<string | null>(null);
  const [cameraOn, setCameraOn] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const barcodeRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const showToast = useToast();

  const { data: products, isLoading, isError } = useProducts();
  const cart = useStore((s) => s.cart);
  const addToCart = useStore((s) => s.addToCart);
  const changeQty = useStore((s) => s.changeQty);
  const clearCart = useStore((s) => s.clearCart);
  const changePrice = useStore((s) => s.changePrice);
  const discount = useStore((s) => s.discount);
  const setDiscount = useStore((s) => s.setDiscount);
  const shift = useStore((s) => s.shift);
  const totals = cartTotals(cart, discount || 0);

  const categories = useMemo(() => ['All', ...new Set((products || []).map((p) => p.cat))], [products]);

  const { subtotal, tax, total } = totals;

  async function lookupBarcode(code: string) {
    const trimmed = code.trim();
    if (!trimmed) return;
    const local = (products || []).find((product) => product.barcode === trimmed || product.sku.toLowerCase() === trimmed.toLowerCase());
    if (local) {
      if (local.stock <= 0) showToast(`${local.name} is out of stock`);
      else addToCart(local);
      setBarcode('');
      return;
    }
    const cached = (await readProducts()).find((product) => product.barcode === trimmed || product.sku.toLowerCase() === trimmed.toLowerCase());
    if (cached) {
      if (cached.stock <= 0) showToast(`${cached.name} is out of stock`);
      else addToCart(cached);
      setBarcode('');
      return;
    }
    try {
      const product = await api.products.byBarcode(trimmed);
      if (product.stock <= 0) showToast(`${product.name} is out of stock`);
      else addToCart(product);
      setBarcode('');
    } catch {
      setUnknownCode(trimmed);
      setBarcode('');
    }
  }

  const filtered = (products || []).filter((p) => {
    if (p.active === false) return false;
    const q = search.toLowerCase();
    const matches = p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q) || (p.barcode || '').includes(search.trim());
    return (cat === 'All' || p.cat === cat) && matches;
  });

  useEffect(() => {
    const onScan = createBarcodeWedge((code) => {
      void lookupBarcode(code);
    });
    function onKey(e: KeyboardEvent) {
      onScan(e);
      const typing = ['INPUT', 'SELECT', 'TEXTAREA'].includes((document.activeElement as HTMLElement)?.tagName);
      if (e.key === 'Escape') {
        setShowPayment(false);
        setShowShift(false);
        setShowRefund(false);
        return;
      }
      if (e.key === '/' && !typing) {
        e.preventDefault();
        searchRef.current?.focus();
      }
      if (e.key === 'Enter' && !typing && cart.length && !showPayment) {
        setShowPayment(true);
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [cart.length, showPayment, products]);

  return (
    <div className="pos-wrap">
      <div className="pos-left">
        <div className="pos-search">
          <button className="shift-chip" onClick={() => setShowShift(true)}>
            <span className="dot" style={{ background: shift.open ? 'var(--good)' : 'var(--ink-faint)' }} />
            <span className="shift-label">{shift.open ? shiftDuration(shift.openedAt) : 'No shift open'}</span>
          </button>
          <div className="search-box" style={{ width: 'auto' }}>
            <Icons.search size={15} />
            <input
              ref={searchRef}
              placeholder="Search products…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <input
            ref={barcodeRef}
            value={barcode}
            placeholder="Scan barcode"
            onChange={(e) => setBarcode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                void lookupBarcode(barcode);
              }
            }}
            style={{ width: 140, border: '1px solid var(--border)', borderRadius: 8, padding: '8px 10px' }}
          />
          <button className="scan-btn" title="Camera barcode" onClick={() => setCameraOn(true)}>
            <Icons.scan size={19} />
          </button>
          <button className="refund-btn" title="Find a sale to refund" onClick={() => setShowRefund(true)}>
            <Icons.undo size={18} />
          </button>
        </div>

        <div className="cat-scroll">
          {categories.map((c) => (
            <button key={c} className={`cat-chip${cat === c ? ' active' : ''}`} onClick={() => setCat(c)}>
              {c}
            </button>
          ))}
        </div>

        <div className="product-scroll">
          {isLoading && (
            <div className="empty">
              <p>Loading products…</p>
            </div>
          )}
          {isError && (
            <div className="empty">
              <h3>Couldn't reach the server</h3>
              <p>Check the connection and try again.</p>
            </div>
          )}
          {products && filtered.length === 0 && (
            <div className="empty">
              <h3>No products found</h3>
              <p>Try a different search term.</p>
            </div>
          )}
          {products && filtered.length > 0 && (
            <div className="product-grid">
              {filtered.map((p) => (
                <button key={p.id} className="prod-tile" disabled={p.stock === 0} style={p.stock === 0 ? { opacity: 0.45, cursor: 'not-allowed' } : undefined} onClick={() => addToCart(p)}>
                  <span className="swatch" style={{ background: p.color }}>
                    {p.emoji}
                  </span>
                  <div>
                    <div className="pname">{p.name}</div>
                    <div className="pprice">{p.stock === 0 ? 'Out of stock' : money(p.price)}</div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="pos-right">
        <div className="cart-head">
          <h3>Current sale</h3>
          <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: 'var(--ink-faint)', fontWeight: 700 }}>
              {cart.length ? `${cart.reduce((a, l) => a + l.qty, 0)} items` : ''}
            </span>
            <span style={{ fontSize: 11, color: 'var(--ink-faint)', fontWeight: 600, opacity: 0.7 }}>/ search · ↵ pay</span>
          </span>
        </div>

        {cart.length === 0 ? (
          <div className="cart-items">
            <div className="cart-empty">
              <Icons.sell size={40} />
              <p>
                Cart is empty
                <br />
                Tap a product to add it
              </p>
            </div>
          </div>
        ) : (
          <>
            <div className="cart-items">
              {cart.map((l) => (
                <div key={l.id} className="cart-line">
                  <span
                    className="swatch"
                    style={{ background: l.color, width: 30, height: 30, borderRadius: 7, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, flexShrink: 0 }}
                  >
                    {l.emoji}
                  </span>
                  <div className="ci-name">
                    <span className="n">{l.name}</span>
                    <input
                      className="p"
                      type="number"
                      value={l.price}
                      onChange={(e) => changePrice(l.id, Number(e.target.value))}
                      style={{ width: 72, border: 'none', background: 'transparent', font: 'inherit', color: 'inherit' }}
                    />
                  </div>
                  <div className="qty-stepper">
                    <button onClick={() => changeQty(l.id, -1)}>−</button>
                    <span className="qn">{l.qty}</span>
                    <button onClick={() => changeQty(l.id, 1)}>+</button>
                  </div>
                  <div className="ci-total">{money(l.price * l.qty)}</div>
                </div>
              ))}
            </div>
            <div className="cart-totals">
              <div className="tot-row">
                <span>Discount</span>
                <input
                  type="number"
                  min={0}
                  value={discount || ''}
                  placeholder="0"
                  onChange={(e) => setDiscount(Number(e.target.value) || 0)}
                  style={{ width: 80, textAlign: 'right' }}
                />
              </div>
              <div className="tot-row">
                <span>Subtotal</span>
                <span>{money(subtotal)}</span>
              </div>
              <div className="tot-row">
                <span>VAT (16%)</span>
                <span>{money(tax)}</span>
              </div>
              <div className="tot-row grand">
                <span>Total</span>
                <span>{money(total)}</span>
              </div>
              <button className="btn btn-primary pay-btn" onClick={() => setShowPayment(true)}>
                Pay {money(total)}
              </button>
            </div>
          </>
        )}
      </div>

      {showPayment && (
        <PaymentOverlay
          cart={cart}
          total={total}
          discount={discount || 0}
          onClose={() => setShowPayment(false)}
          onComplete={() => {
            clearCart();
            setShowPayment(false);
          }}
          showToast={showToast}
        />
      )}
      {unknownCode && (
        <UnknownBarcode
          code={unknownCode}
          onClose={() => setUnknownCode(null)}
          onCreated={(product) => {
            addToCart(product);
            setUnknownCode(null);
            showToast(`${product.name} added`);
          }}
        />
      )}
      {cameraOn && (
        <CameraScan
          videoRef={videoRef}
          onClose={() => setCameraOn(false)}
          onCode={(code) => {
            setCameraOn(false);
            void lookupBarcode(code);
          }}
        />
      )}
      {showShift && <ShiftDrawer onClose={() => setShowShift(false)} />}
      {showRefund && <RefundDrawer onClose={() => setShowRefund(false)} />}
    </div>
  );
}

function UnknownBarcode({ code, onClose, onCreated }: { code: string; onClose: () => void; onCreated: (product: Product) => void }) {
  const role = useStore((s) => s.user?.role);
  if (role === 'CASHIER') {
    return (
      <div className="pos-overlay">
        <div className="receipt-wrap">
          <h2>Unknown barcode</h2>
          <p className="msg">{code} is not on this device. A manager has to create the product. Nothing was added.</p>
          <button className="btn btn-primary" onClick={onClose}>Back</button>
        </div>
      </div>
    );
  }
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [cost, setCost] = useState('');
  const [qty, setQty] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function save() {
    const priceNum = Number(price);
    if (!name || !priceNum) return;
    setBusy(true);
    setError('');
    try {
      const product = await api.products.create({ name, price: priceNum, cost: cost ? Number(cost) : undefined, sku: code, barcode: code, cat: 'Grocery' });
      const received = qty ? await api.products.receive(product.id, { qty: Number(qty), cost: cost ? Number(cost) : undefined, supplier: 'Opening scan' }) : product;
      onCreated(received);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the product');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="pos-overlay">
      <div className="receipt-wrap" style={{ textAlign: 'left' }}>
        <h2>Unknown barcode</h2>
        <p className="msg">{code} is not in the catalogue. Create the product yourself — nothing is created automatically.</p>
        <div className="form-field">
          <label>Product name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="form-row2">
          <div className="form-field">
            <label>Selling price</label>
            <input type="number" value={price} onChange={(e) => setPrice(e.target.value)} />
          </div>
          <div className="form-field">
            <label>Buying cost</label>
            <input type="number" value={cost} onChange={(e) => setCost(e.target.value)} />
          </div>
        </div>
        <div className="form-field">
          <label>Quantity to receive now</label>
          <input type="number" value={qty} placeholder="Leave blank to add the product with zero stock" onChange={(e) => setQty(e.target.value)} />
        </div>
        {error && <p style={{ color: 'var(--bad)', fontSize: 13 }}>{error}</p>}
        <div className="receipt-actions">
          <button className="btn btn-primary" disabled={busy || !name || !price} onClick={() => void save()}>
            {busy ? 'Saving…' : 'Create product'}
          </button>
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  );
}

function CameraScan({ videoRef, onClose, onCode }: { videoRef: React.RefObject<HTMLVideoElement | null>; onClose: () => void; onCode: (code: string) => void }) {
  const [message, setMessage] = useState('Point the camera at a barcode');
  const onCodeRef = useRef(onCode);
  onCodeRef.current = onCode;

  useEffect(() => {
    let stopped = false;
    let stream: MediaStream | null = null;
    const Detector = (window as unknown as { BarcodeDetector?: new (opts: { formats: string[] }) => { detect: (source: HTMLVideoElement) => Promise<{ rawValue: string }[]> } }).BarcodeDetector;
    if (!Detector || !navigator.mediaDevices) {
      setMessage('This browser has no camera barcode detector. Use the barcode field or a hardware scanner.');
      return;
    }
    const detector = new Detector({ formats: ['ean_13', 'ean_8', 'code_128', 'upc_a', 'upc_e', 'qr_code'] });
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } }).then((media) => {
      stream = media;
      if (videoRef.current) {
        videoRef.current.srcObject = media;
        void videoRef.current.play();
      }
      const tick = async () => {
        if (stopped || !videoRef.current) return;
        try {
          const codes = await detector.detect(videoRef.current);
          if (codes[0]?.rawValue) {
            onCodeRef.current(codes[0].rawValue);
            return;
          }
        } catch {
          // keep scanning
        }
        if (!stopped) requestAnimationFrame(() => void tick());
      };
      void tick();
    }).catch(() => setMessage('Camera permission was blocked.'));
    return () => {
      stopped = true;
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, [videoRef]);

  return (
    <div className="pos-overlay">
      <div className="receipt-wrap">
        <h2>Camera scan</h2>
        <video ref={videoRef} style={{ width: '100%', borderRadius: 12, background: '#111' }} muted playsInline />
        <p className="msg">{message}</p>
        <button className="btn btn-ghost" onClick={onClose}>Close</button>
      </div>
    </div>
  );
}
