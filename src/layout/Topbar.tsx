import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Icons } from '../components/Icons';
import { PAGE_TITLES } from './navConfig';
import { useStore } from '../store/useStore';
import { useCapabilities, useProducts, useCustomers } from '../lib/queries';

export function Topbar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { data: caps } = useCapabilities();
  const { data: products } = useProducts();
  const { data: customers } = useCustomers();
  const currentBranch = useStore((s) => s.currentBranch);
  const setCurrentBranch = useStore((s) => s.setCurrentBranch);

  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  const [title, sub] = PAGE_TITLES[location.pathname] || ['Duka', ''];
  const isSell = location.pathname === '/sell';

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) {
        setQuery('');
        if (window.innerWidth <= 700) setExpanded(false);
      }
    }
    document.addEventListener('click', onDocClick);
    return () => document.removeEventListener('click', onDocClick);
  }, []);

  const prodHits = query ? (products || []).filter((p) => p.name.toLowerCase().includes(query.toLowerCase())).slice(0, 4) : [];
  const custHits = query ? (customers || []).filter((c) => c.name.toLowerCase().includes(query.toLowerCase())).slice(0, 3) : [];
  const hasResults = prodHits.length > 0 || custHits.length > 0;

  return (
    <div className="topbar">
      <h1>{title}</h1>
      {sub && <span className="sub">{sub}</span>}

      {caps?.multiBranch && !isSell && (
        <select className="branch-switcher" value={currentBranch} onChange={(e) => setCurrentBranch(e.target.value)}>
          <option>All branches</option>
          <option>Nairobi — Moi Avenue</option>
          <option>Mombasa — Nyali</option>
          <option>Kisumu — CBD</option>
        </select>
      )}

      {!isSell && (
        <div
          ref={boxRef}
          className={`search-box${expanded ? ' expanded' : ''}`}
          style={{ marginLeft: caps?.multiBranch ? undefined : 'auto' }}
          onClick={() => {
            if (window.innerWidth <= 700 && !expanded) setExpanded(true);
          }}
        >
          <Icons.search size={15} />
          <input
            placeholder="Search products, customers…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query && (
            <div className="gs-results">
              {!hasResults && (
                <div style={{ padding: 16, fontSize: 12.5, color: 'var(--ink-faint)', textAlign: 'center' }}>
                  No matches for "{query}"
                </div>
              )}
              {prodHits.length > 0 && <div className="gs-label">PRODUCTS</div>}
              {prodHits.map((p) => (
                <div
                  key={p.id}
                  className="gs-hit"
                  onClick={() => {
                    navigate('/products');
                    setQuery('');
                  }}
                >
                  <span
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: 7,
                      background: p.color,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 13,
                    }}
                  >
                    {p.emoji}
                  </span>
                  {p.name}
                </div>
              ))}
              {custHits.length > 0 && <div className="gs-label">CUSTOMERS</div>}
              {custHits.map((c) => (
                <div
                  key={c.id}
                  className="gs-hit"
                  onClick={() => {
                    navigate('/customers');
                    setQuery('');
                  }}
                >
                  <span
                    style={{
                      width: 26,
                      height: 26,
                      borderRadius: '50%',
                      background: 'var(--brand-soft)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 11,
                      fontWeight: 700,
                      color: 'var(--brand)',
                    }}
                  >
                    {c.name[0]}
                  </span>
                  {c.name}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="topbar-icon" title="Alerts">
        <Icons.bell size={17} />
      </div>
      <div className="avatar">AN</div>
    </div>
  );
}
