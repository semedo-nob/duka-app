# Duka — Business OS

A real React + TypeScript implementation of the POS/business-OS prototype, replacing the single-file HTML
version with actual routing, component structure, and state management.

## Stack

- **Vite + React 19 + TypeScript** — build tooling and framework
- **react-router-dom** — real routes per screen (`/`, `/sell`, `/products`, `/inventory`, `/customers`,
  `/purchasing`, `/expenses`, `/branches`, `/reports`, `/team`, `/etims`, `/audit`, `/settings`,
  `/settings/billing`, `/login`, `/setup`)
- **zustand** — global store for cart, unlocked capabilities, purchase orders, shift state, and onboarding data
- Plain CSS with custom properties (no Tailwind) — ported directly from the original design tokens, so the
  visual language is unchanged

## Running it

```bash
npm install
npm run dev:all   # runs the Vite dev server AND the API together
```

This starts the frontend at http://localhost:5173 and the API at http://localhost:4000. The frontend expects
the API at `http://localhost:4000/api` by default — override with a `.env` file (`VITE_API_URL=...`) if you run
the API somewhere else.

Run them separately if you prefer:

```bash
npm run server   # API only, on :4000
npm run dev      # frontend only, on :5173
```

You'll land on the login screen first — "Log in" drops you straight into the dashboard with data from the API,
or "Set up a new business" walks through the onboarding wizard.

## Project structure

```
src/
  components/        Shared UI: Icons, Drawer, Toast, Shared (Metric/AlertRow/StockPill/BreakdownRow)
  components/pos/     POS-specific: PaymentOverlay, ShiftDrawer, RefundDrawer
  data/               Mock data (products, customers, purchase orders, recent sales)
  layout/             Sidebar, Topbar, BottomNav (+ More sheet), AppShell, nav config
  lib/                Formatting helpers
  pages/              One file per route
  store/              Zustand store (cart, capabilities, shift, onboarding)
  styles/             tokens.css (design system), layout.css, pages.css
```

## What's real vs. what's still mock data

- **Real, interactive:** cart math, payment flows (cash/M-Pesa/card/split with change calculation), the
  card-decline demo error state, shift open/close with variance calculation, refunds, capability
  unlock/lock state (persists across the whole app via the store), the onboarding wizard, global search,
  keyboard shortcuts (`/` to search, `Enter` to pay, `Esc` to close), and the mobile "More" sheet.
- **Mock/static:** product and customer data, dashboard metrics, report numbers — there's no backend. Swapping
  `src/data/mockData.ts` for real API calls (and wiring the relevant pages to `useEffect`/`react-query` or
  similar) is the natural next step.

## Build

```bash
npm run build
```

Type-checks with `tsc -b` and produces a production bundle in `dist/`.
