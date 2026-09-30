import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { AuthUser, Product } from '../lib/api';
import { authToken } from '../lib/api';

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
  openedAt?: string;
};

type Store = {
  // cart (client-only — the in-progress sale, not committed until checkout)
  cart: CartLine[];
  addToCart: (p: Product) => void;
  changeQty: (id: number, delta: number) => void;
  clearCart: () => void;
  changePrice: (id: number, price: number) => void;
  discount: number;
  setDiscount: (amount: number) => void;

  // onboarding
  setupData: SetupData;
  setSetupData: (d: Partial<SetupData>) => void;
  loggedIn: boolean;
  user: AuthUser | null;
  logIn: (user?: AuthUser | null) => void;
  logOut: () => void;

  // shift (client-only demo state — a real version would live server-side too)
  shift: ShiftState;
  openShift: (openingCash: number) => void;
  closeShift: () => void;

  // branch
  currentBranch: string;
  setCurrentBranch: (b: string) => void;
};

export const useStore = create<Store>()(
  persist(
    (set) => ({
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
  clearCart: () => set({ cart: [], discount: 0 }),
  changePrice: (id, price) =>
    set((state) => ({
      cart: state.cart.map((line) => (line.id === id ? { ...line, price: Math.max(0, price) } : line)),
    })),
  discount: 0,
  setDiscount: (amount) => set({ discount: Math.max(0, amount) }),

  setupData: { businessName: '', businessType: 'Retail shop', category: null },
  setSetupData: (d) => set((state) => ({ setupData: { ...state.setupData, ...d } })),
  loggedIn: !!authToken(),
  user: null,
  logIn: (user) => set({ loggedIn: true, user: user ?? null }),
  logOut: () => set({ loggedIn: false, user: null, cart: [], discount: 0 }),

  shift: { open: false, openingCash: 0, opened: '' },
  openShift: (openingCash) => set({ shift: { open: true, openingCash, opened: new Date().toLocaleTimeString('en-KE', { hour: 'numeric', minute: '2-digit' }), openedAt: new Date().toISOString() } }),
  closeShift: () => set((state) => ({ shift: { ...state.shift, open: false } })),

  currentBranch: 'All branches',
  setCurrentBranch: (b) => set({ currentBranch: b }),
    }),
    {
      name: 'duka-pos',
      version: 2,
      migrate: (persisted) => {
        const state = persisted as { shift?: ShiftState };
        if (state.shift?.opened === '11:40am' && state.shift.openingCash === 5000) {
          state.shift = { open: false, openingCash: 0, opened: '' };
        }
        return state as Store;
      },
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        cart: state.cart,
        discount: state.discount,
        shift: state.shift,
        currentBranch: state.currentBranch,
        setupData: state.setupData,
        user: state.user,
        loggedIn: state.loggedIn,
      }),
    },
  ),
);
