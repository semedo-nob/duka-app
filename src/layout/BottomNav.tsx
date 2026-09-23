import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Icons } from '../components/Icons';
import { MORE_SHEET_ITEMS } from './navConfig';
import { useCapabilities } from '../lib/queries';

export function BottomNav() {
  const [showMore, setShowMore] = useState(false);
  const navigate = useNavigate();
  const { data: caps } = useCapabilities();

  return (
    <>
      <div className="bottombar">
        <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
          <Icons.dashboard size={19} />
          Home
        </NavLink>
        <NavLink to="/sell" className={({ isActive }) => (isActive ? 'active' : '')}>
          <Icons.sell size={19} />
          Sell
        </NavLink>
        <NavLink to="/products" className={({ isActive }) => (isActive ? 'active' : '')}>
          <Icons.box size={19} />
          Products
        </NavLink>
        <NavLink to="/inventory" className={({ isActive }) => (isActive ? 'active' : '')}>
          <Icons.layers size={19} />
          Stock
        </NavLink>
        <button onClick={() => setShowMore(true)}>
          <Icons.dots size={19} />
          More
        </button>
      </div>

      {showMore && (
        <div className="sheet-backdrop" onClick={(e) => e.target === e.currentTarget && setShowMore(false)}>
          <div className="sheet">
            <div className="sheet-handle" />
            <div className="sheet-grid">
              {MORE_SHEET_ITEMS.map((item) => {
                const Icon = (Icons as Record<string, typeof Icons.box>)[item.icon];
                const locked = item.lockKey && !caps?.[item.lockKey];
                return (
                  <button
                    key={item.path}
                    className="sheet-item"
                    onClick={() => {
                      setShowMore(false);
                      navigate(item.path);
                    }}
                  >
                    <span className="ic">
                      <Icon size={19} />
                      {locked && (
                        <span className="lockdot">
                          <Icons.lock size={9} />
                        </span>
                      )}
                    </span>
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
