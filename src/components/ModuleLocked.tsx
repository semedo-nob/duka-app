import { useNavigate } from 'react-router-dom';
import { Icons } from './Icons';

export function ModuleLocked({ title, body }: { title: string; body: string }) {
  const navigate = useNavigate();
  return (
    <div className="empty" style={{ maxWidth: 460, margin: '40px auto' }}>
      <Icons.lock size={46} color="var(--ink-faint)" />
      <h3>{title}</h3>
      <p>{body}</p>
      <p>This stays locked until the payment provider verifies a subscription for this business. There is no local unlock.</p>
      <button className="btn btn-accent" onClick={() => navigate('/settings/billing')}>
        View plans
      </button>
    </div>
  );
}
