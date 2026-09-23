import { NavLink, useNavigate } from 'react-router-dom';
import { Icons } from '../components/Icons';
import { PRIMARY_NAV, MANAGE_NAV, INSIGHT_NAV } from './navConfig';
import { useCapabilities } from '../lib/queries';
import type { CapabilityKey } from '../lib/api';

function NavButton({
  path,
  label,
  icon,
  lockKey,
  locked,
}: {
  path: string;
  label: string;
  icon: string;
  lockKey?: CapabilityKey;
  locked: boolean;
}) {
  const Icon = (Icons as Record<string, typeof Icons.box>)[icon];
  return (
    <NavLink to={path} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
      <Icon size={18} />
      {label}
      {lockKey && locked && (
        <span className="lock">
          <Icons.lock size={13} />
        </span>
      )}
    </NavLink>
  );
}

export function Sidebar() {
  const navigate = useNavigate();
  const { data: caps } = useCapabilities();
  const unlockedCount = 4 + Object.values(caps || {}).filter(Boolean).length;

  return (
    <nav className="sidebar">
      <div className="brand-mark">
        <span className="dot" />
        <span>Duka</span>
      </div>

      <div className="nav-group">
        {PRIMARY_NAV.map((n) => (
          <NavButton key={n.path} {...n} locked={!!n.lockKey && !caps?.[n.lockKey]} />
        ))}
      </div>

      <div className="nav-group">
        <div className="nav-label">MANAGE</div>
        {MANAGE_NAV.map((n) => (
          <NavButton key={n.path} {...n} locked={!!n.lockKey && !caps?.[n.lockKey]} />
        ))}
      </div>

      <div className="nav-group">
        <div className="nav-label">INSIGHT</div>
        {INSIGHT_NAV.map((n) => (
          <NavButton key={n.path} {...n} locked={!!n.lockKey && !caps?.[n.lockKey]} />
        ))}
      </div>

      <div className="sidebar-foot">
        <button className="plan-chip" onClick={() => navigate('/settings/billing')}>
          <div>
            <div className="t1">Growth plan</div>
            <div className="t2">{unlockedCount} of 9 unlocked</div>
          </div>
          <Icons.chevronRight size={15} color="#fff" />
        </button>
      </div>
    </nav>
  );
}
