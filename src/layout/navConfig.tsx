import type { CapabilityKey } from '../lib/api';

export type NavItem = {
  path: string;
  label: string;
  icon: string;
  lockKey?: CapabilityKey;
};

export const PRIMARY_NAV: NavItem[] = [
  { path: '/', label: 'Dashboard', icon: 'dashboard' },
  { path: '/sell', label: 'Sell', icon: 'sell' },
];

export const MANAGE_NAV: NavItem[] = [
  { path: '/products', label: 'Products', icon: 'box' },
  { path: '/inventory', label: 'Inventory', icon: 'layers' },
  { path: '/customers', label: 'Customers', icon: 'user' },
  { path: '/purchasing', label: 'Purchasing', icon: 'truck', lockKey: 'purchasing' },
  { path: '/expenses', label: 'Expenses', icon: 'wallet' },
  { path: '/branches', label: 'Branches', icon: 'branch', lockKey: 'multiBranch' },
];

export const INSIGHT_NAV: NavItem[] = [
  { path: '/reports', label: 'Reports', icon: 'bars' },
  { path: '/team', label: 'Team', icon: 'users' },
  { path: '/etims', label: 'eTIMS', icon: 'doc', lockKey: 'etims' },
  { path: '/audit', label: 'Audit log', icon: 'clock' },
  { path: '/settings', label: 'Settings', icon: 'gear' },
];

export const MORE_SHEET_ITEMS: NavItem[] = [
  ...MANAGE_NAV.slice(2),
  ...INSIGHT_NAV,
];

export const PAGE_TITLES: Record<string, [string, string]> = {
  '/': ['Good afternoon, Amina', 'Wednesday, 10 September'],
  '/sell': ['Sell', ''],
  '/products': ['Products', 'Manage your catalogue'],
  '/inventory': ['Inventory', 'Track stock across your shop'],
  '/customers': ['Customers', 'Everyone who buys from you'],
  '/purchasing': ['Purchasing', ''],
  '/expenses': ['Expenses', 'Money spent running the business'],
  '/branches': ['Branches', 'Manage your locations'],
  '/reports': ['Reports', 'How the business is doing'],
  '/team': ['Team', 'Who has access'],
  '/etims': ['eTIMS', 'Tax invoice submission status'],
  '/audit': ['Audit log', 'Who changed what'],
  '/settings': ['Settings', ''],
  '/settings/billing': ['Capabilities', "Unlock more of Duka as you grow"],
};
