import { useEffect, useMemo, useRef, useState } from 'react';
import { Icons } from '../components/Icons';
import { PaymentOverlay } from '../components/pos/PaymentOverlay';
import { ShiftDrawer } from '../components/pos/ShiftDrawer';
import { RefundDrawer } from '../components/pos/RefundDrawer';
import { useStore } from '../store/useStore';
import { money } from '../lib/format';
import { useToast } from '../components/Toast';
import { useProducts } from '../lib/queries';

export default function Sell() {
  const [search, setSearch] = useState('');
  const [cat, setCat] = useState('All');
  const [showPayment, setShowPayment] = useState(false);
  const [showShift, setShowShift] = useState(false);
  const [showRefund, setShowRefund] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const showToast = useToast();

  const { data: products, isLoading, isError } = useProducts();
  const cart = useStore((s) => s.cart);
  const addToCart = useStore((s) => s.addToCart);
  const changeQty = useStore((s) => s.changeQty);
  const clearCart = useStore((s) => s.clearCart);
  const shift = useStore((s) => s.shift);

  const categories = useMemo(() => ['All', ...new Set((products || []).map((p) => p.cat))], [products]);

  const subtotalWithTax = cart.reduce((a, l) => a + l.price * l.qty, 0);
  const tax = Math.round(subtotalWithTax * 0.16);
  const total = subtotalWithTax;

  const filtered = (products || []).filter(
    (p) => (cat === 'All' || p.cat === cat) && p.name.toLowerCase().includes(search.toLowerCase())
  );

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
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
  }, [cart.length, showPayment]);

  return (
    <div className="pos-wrap">
      <div className="pos-left">
        <div className="pos-search">
          <button className="shift-chip" onClick={() => setShowShift(true)}>
            <span className="dot" style={{ background: shift.open ? 'var(--good)' : 'var(--ink-faint)' }} />
            <span className="shift-label">{shift.open ? 'Shift open · 3h 20m' : 'No shift open'}</span>
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
          <button className="scan-btn" title="Scan barcode">
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
              <p>Make sure the API is running (npm run server) at the URL in VITE_API_URL.</p>
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
                    <span className="p">{money(l.price)}</span>
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
                <span>Subtotal</span>
                <span>{money(subtotalWithTax - tax)}</span>
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
          onClose={() => setShowPayment(false)}
          onComplete={() => {
            clearCart();
            setShowPayment(false);
          }}
          showToast={showToast}
        />
      )}
      {showShift && <ShiftDrawer onClose={() => setShowShift(false)} />}
      {showRefund && <RefundDrawer onClose={() => setShowRefund(false)} />}
    </div>
  );
}
