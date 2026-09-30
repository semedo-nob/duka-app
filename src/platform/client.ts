const KEY = 'duka.platform.token';

export function platformToken() {
  return localStorage.getItem(KEY);
}

export function setPlatformToken(token: string | null) {
  if (token) localStorage.setItem(KEY, token);
  else localStorage.removeItem(KEY);
}

function base() {
  const desktop = (window as Window & { dukaDesktop?: { apiBase?: string } }).dukaDesktop?.apiBase;
  return (desktop && desktop.length > 0 ? desktop : import.meta.env.VITE_API_URL || 'http://localhost:4000/api').replace(/\/$/, '');
}

export async function platformRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const headers = new Headers(options?.headers);
  if (!headers.has('Content-Type') && options?.body) headers.set('Content-Type', 'application/json');
  const token = platformToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${base()}/platform${path}`, { ...options, headers });
  if (res.status === 401 && path !== '/login') {
    setPlatformToken(null);
    if (!window.location.pathname.startsWith('/platform/login')) window.location.assign('/platform/login');
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Request failed: ${res.status}`);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export type PlatformSession = { name: string; role: string };

export const platformApi = {
  login: (phone: string, pin: string) =>
    platformRequest<{ token: string; name: string; role: string }>('/login', { method: 'POST', body: JSON.stringify({ phone, pin }) }),
  me: () => platformRequest<PlatformSession>('/me'),
  dashboard: () => platformRequest<Record<string, number | { action: string; actor: string; at: string; detail: string; businessId?: number }[]>>('/dashboard'),
  businesses: () => platformRequest<Record<string, unknown>[]>('/businesses'),
  business: (id: number) => platformRequest<Record<string, unknown>>(`/businesses/${id}`),
  diagnostics: (id: number) => platformRequest<Record<string, unknown>>(`/businesses/${id}/diagnostics`),
  approve: (id: number, reason: string) => platformRequest<unknown>(`/businesses/${id}/approve`, { method: 'POST', body: JSON.stringify({ reason }) }),
  suspend: (id: number, reason: string) => platformRequest<unknown>(`/businesses/${id}/suspend`, { method: 'POST', body: JSON.stringify({ reason }) }),
  reactivate: (id: number, reason: string) => platformRequest<unknown>(`/businesses/${id}/reactivate`, { method: 'POST', body: JSON.stringify({ reason }) }),
  closeBusiness: (id: number, reason: string) => platformRequest<unknown>(`/businesses/${id}/close`, { method: 'POST', body: JSON.stringify({ reason }) }),
  grant: (id: number, body: { planCode: string; days: number; reason: string; source: 'ADMIN_GRANT' | 'PROMOTIONAL' }) =>
    platformRequest<Record<string, unknown>>(`/businesses/${id}/grants`, { method: 'POST', body: JSON.stringify(body) }),
  ownerRecovery: (id: number) => platformRequest<{ code: string; ownerName: string }>(`/businesses/${id}/owner-recovery`, { method: 'POST' }),
  admins: () => platformRequest<{ id: number; name: string; phone: string; role: string; active: boolean }[]>('/admins'),
  support: () => platformRequest<Record<string, unknown>[]>('/support'),
  supportCase: (id: number) => platformRequest<{ messages: { authorKind: string; authorName: string; body: string }[]; status: string; topic: string; category: string; context?: Record<string, unknown> }>(`/support/${id}`),
  reply: (id: number, message: string) => platformRequest<unknown>(`/support/${id}/reply`, { method: 'POST', body: JSON.stringify({ message }) }),
  status: (id: number, status: string) => platformRequest<unknown>(`/support/${id}/status?status=${encodeURIComponent(status)}`, { method: 'POST' }),
  subscriptions: () => platformRequest<Record<string, unknown>[]>('/subscriptions'),
  plans: () => platformRequest<Record<string, unknown>[]>('/plans'),
  savePlan: (creating: boolean, body: unknown) =>
    platformRequest<unknown>(creating ? '/plans' : `/plans/${(body as { code: string }).code}`, { method: creating ? 'POST' : 'PUT', body: JSON.stringify(body) }),
  payments: () => platformRequest<Record<string, unknown>[]>('/payments'),
  events: () => platformRequest<Record<string, unknown>[]>('/billing-events'),
  audit: () => platformRequest<Record<string, unknown>[]>('/audit'),
  createAdmin: (body: { name: string; phone: string; pin: string; role: string }) =>
    platformRequest<unknown>('/admins', { method: 'POST', body: JSON.stringify(body) }),
};
