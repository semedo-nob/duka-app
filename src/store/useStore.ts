import { create } from 'zustand';
import type { Product } from '../lib/api';

export type CartLine = {
  id: number;
  name: string;
  price: number;
  emoji: string;
  color: string;
  qty: number;
};

type SetupData = {
  businessName: string;
  businessType: string;
  category: string | null;
};

type ShiftState = {
  open: boolean;
  openingCash: number;
  opened: string;
};

type Store = {
  // cart (client-only — the in-progress sale, not committed until checkout)
  cart: CartLine[];
  addToCart: (p: Product) => void;
  changeQty: (id: number, delta: number) => void;
  clearCart: () => void;

  // onboarding
  setupData: SetupData;
  setSetupData: (d: Partial<SetupData>) => void;
  loggedIn: boolean;
  logIn: () => void;

  // shift (client-only demo state — a real version would live server-side too)
  shift: ShiftState;
  closeShift: () => void;

  // branch
  currentBranch: string;
  setCurrentBranch: (b: string) => void;
};

export const useStore = create<Store>((set) => ({
  cart: [],
  addToCart: (p) =>
    set((state) => {
      const existing = state.cart.find((l) => l.id === p.id);
      if (existing) {
        return { cart: state.cart.map((l) => (l.id === p.id ? { ...l, qty: l.qty + 1 } : l)) };
      }
      return { cart: [...state.cart, { id: p.id, name: p.name, price: p.price, emoji: p.emoji, color: p.color, qty: 1 }] };
    }),
  changeQty: (id, delta) =>
    set((state) => ({
      cart: state.cart.map((l) => (l.id === id ? { ...l, qty: l.qty + delta } : l)).filter((l) => l.qty > 0),
    })),
  clearCart: () => set({ cart: [] }),

  setupData: { businessName: '', businessType: 'Retail shop', category: null },
  setSetupData: (d) => set((state) => ({ setupData: { ...state.setupData, ...d } })),
  loggedIn: false,
  logIn: () => set({ loggedIn: true }),

  shift: { open: true, openingCash: 5000, opened: '11:40am' },
  closeShift: () => set((state) => ({ shift: { ...state.shift, open: false } })),

  currentBranch: 'All branches',
  setCurrentBranch: (b) => set({ currentBranch: b }),
}));
