# Duka POS

Duka is a point of sale and back office for a Kenyan shop. The till runs in the browser or as a Linux desktop app. Stock, sales, subscriptions, and support are decided by a Spring Boot API and PostgreSQL. The till can sell offline and sync later. The server remains the authority for stock, payment, and which modules are unlocked.

## Features

| Area | State |
| --- | --- |
| POS, products, inventory, customers, expenses, reports | Implemented. Covered by the API test suite and the till's unit tests. |
| Purchasing, customer credit, advanced reports, multi-branch, eTIMS queue | Implemented as plan entitlements. Locked until the server says the plan includes them. |
| Barcode scanning | Implemented for a USB keyboard-wedge scanner. Needs a physical scanner on the till. |
| Receipt printing | Implemented through the desktop print bridge. Needs a physical CUPS printer. |
| Offline sales | Implemented with IndexedDB and an outbox. Covered by unit tests. A full restart-and-sync check on a packaged till still belongs on the acceptance list in `docs/PRODUCTION.md`. |
| M-Pesa sale payments | Implemented boundary. Live mode needs Daraja credentials and a public callback. Development can use `MPESA_MODE=mock`. |
| eTIMS | Implemented boundary. Stays unconfigured until KRA credentials are supplied. Nothing is marked accepted without that. |
| Paystack billing | Implemented. A charge unlocks modules only after Paystack verify or a signed webhook. Needs production keys and a public webhook URL. |
| Subscriptions and entitlements | Implemented. Trial, Paystack, admin grant, and promotional grant are stored as different sources. |
| Customer support | Implemented. Tickets and replies live on the API. |
| Super Admin | Implemented at `/platform`, with a separate login from the shop. |
| Diagnostics and audit | Implemented. |

## Architecture

```text
React / Vite / TypeScript
        |
     Electron          (optional desktop shell)
        |
    Spring Boot        (hosted API)
        |
    PostgreSQL
```

The till keeps a local IndexedDB database: catalogue, sales that have not synced, and a device id. Each offline sale has an idempotency key. The API stores that sale once. A repeated sync does not move stock again.

```text
Client PC
   |
Duka Electron app
   |  HTTPS
Duka API
   |
PostgreSQL
```

The shop does not need Java or Postgres on the till. Those run on the host you choose for the API.

## Repository structure

```text
/
├── backend/          Spring Boot API, Flyway migrations, tests
├── src/              React till and /platform console
├── electron/         Desktop shell
├── public/
├── scripts/          Local database, API, and web scripts
├── docs/PRODUCTION.md
├── docker-compose.yml
├── package.json
└── README.md
```

`server/` is an older Node stub. The API to run is `backend/`.

## Development setup

You need Node, a JDK that can run Gradle 8.10, and Docker for Postgres (or your own Postgres on port 5434).

Copy `.env.example` if you want to override the laptop defaults. Do not commit that copy with real secrets. The dev profile already has local defaults in `backend/src/main/resources/application-dev.yml`.

## Running locally

```bash
npm run db      # Postgres container duka-pg on port 5434
npm run server  # Spring Boot on port 4000, profile dev
npm run dev     # Vite on port 5173
```

Open http://localhost:5173. The API is http://localhost:4000. Operators use http://localhost:5173/platform.

`npm run db` reuses the container named `duka-pg`. It does not create a second database and it does not delete the volume.

### Development logins

These exist only on the `dev` and `test` profiles. Production refuses the platform phone and PIN below.

| Who | Phone | Secret | Where |
| --- | --- | --- | --- |
| Shop owner (bootstrap) | `0712345678` | PIN `changeme` | `/login` |
| Platform super admin | `0700000000` | PIN `123456` | `/platform/login` |

Change `DUKA_BOOTSTRAP_PHONE`, `DUKA_BOOTSTRAP_PIN`, `DUKA_PLATFORM_ADMIN_PHONE`, and `DUKA_PLATFORM_ADMIN_PIN` before any shared deployment.

## Testing

```bash
cd backend && ./gradlew test --offline
npx vitest run
npx tsc -b
```

Backend tests use the `duka_test` database on the same Postgres. Create it once:

```sql
CREATE DATABASE duka_test;
```

## What a new business goes through

1. The owner registers at `/setup`. The business is `PENDING_APPROVAL`. They can sign in and open Support. The API refuses sales until a super admin approves the business.
2. A super admin reviews the business at `/platform/businesses` and approves it with a reason. Approval starts a 14-day Core trial. Paid modules stay locked.
3. The owner sells, receives stock, and can work offline. The till writes the sale locally first and syncs with the same idempotency key.
4. A locked module links to `/settings/billing`. Checkout calls Paystack. The module unlocks only after Duka verifies the charge or accepts a signed webhook.
5. If the customer cannot pay online, a super admin or billing admin can activate a plan from Business 360. That is an admin grant, not a Paystack payment.
6. Support tickets are stored on the API. The customer and the platform operator see the same thread.

Suspension is separate from an expired subscription. Expired paid modules lock. Products, sales, and customers remain. A suspended business cannot sell even if the subscription is still active.

## Billing

Paystack is the subscription provider. Kenya M-Pesa uses Paystack's charge API (`mobile_money` provider `mpesa`). Paystack sends the prompt. Card and other channels use Paystack hosted checkout (`transaction/initialize`). Duka does not implement its own STK push for subscriptions.

Set these on the **API process**, not in the till:

| Variable | Where it goes |
| --- | --- |
| `DUKA_BILLING_PROVIDER` | `unconfigured`, `mock`, or `paystack` |
| `DUKA_BILLING_SECRET_KEY` | Paystack secret key. Server only. |
| `DUKA_BILLING_PUBLIC_KEY` | Paystack public key. Not required for the current server checkout. |
| `DUKA_BILLING_WEBHOOK_SECRET` | Optional. Used when Paystack's webhook signing key differs from the secret key. |
| `DUKA_BILLING_CALLBACK_URL` | Browser return URL, for example `https://shop.example.com/settings/billing`. |
| `DUKA_BILLING_WEBHOOK_URL` | The public URL you paste into the Paystack dashboard: `https://api.example.com/api/integrations/billing/webhook`. |

Modes:

- `unconfigured` — checkout returns an error and modules stay locked. This is the safe default.
- `mock` — development and test only. A mock checkout activates a plan and is labeled `MOCK`. It does not create a Paystack payment. The `prod` profile refuses to start if this is set.
- `paystack` — production. Duka calls Paystack `transaction/verify` and checks `x-paystack-signature` (HMAC SHA-512). A browser return is not proof of payment.

The secret stays on the server. The same event or reference updates one payment and one subscription.

## Admin activation

From Business 360, a `SUPER_ADMIN` or `BILLING_ADMIN` can activate a plan for a number of days with a reason. The source is `ADMIN_GRANT` or `PROMOTIONAL`. Support admins and auditors cannot do this. The audit event records the operator, business, plan, start, expiry, reason, and activation type. No Paystack payment row is created.

## M-Pesa

Sale payments use Daraja when `MPESA_MODE=live` and the consumer key, secret, shortcode, passkey, and `MPESA_CALLBACK_URL` are set. `MPESA_MODE=mock` is for development. Live mode does not invent a successful payment.

## eTIMS

Set `ETIMS_MODE`, `ETIMS_BASE_URL`, and `ETIMS_TIN` when a real KRA adapter should be used. Until then submissions stay pending. This has not been validated against KRA production.

## Super Admin

`/platform/login` is a separate JWT from the shop. A platform token cannot post a sale. A shop token cannot open the platform API.

| Role | Can |
| --- | --- |
| `SUPER_ADMIN` | Approve, suspend, reactivate, and close businesses. Manage plans, billing, support, audit, and platform users. Grant a plan. |
| `BILLING_ADMIN` | View payments and billing events, manage plans, grant a plan. |
| `SUPPORT_ADMIN` | View the business, reply to tickets, read diagnostics, issue an owner recovery code. |
| `PLATFORM_AUDITOR` | View subscriptions, plans, and audit. Cannot suspend a business, create a plan, or grant one. |

The first super admin is created on startup when `platform_admins` is empty and `DUKA_PLATFORM_ADMIN_PHONE` / `DUKA_PLATFORM_ADMIN_PIN` are set. The development pair is rejected unless the profile is `dev` or `test`.

## Authentication

Till login is phone plus PIN. The PIN is stored as a bcrypt hash. Operators never see it. Offline unlock uses a local verifier after one online login.

Account password (owner or manager) is required for subscription changes, ownership transfer, and closing the business.

Forgot PIN: the login screen asks for the phone number. SMS is not configured, so the customer sees that online recovery is unavailable and should contact Duka Support. A super admin or support admin can issue a one-time recovery code from Business 360. The code is stored as a hash, expires in two hours, and works once. The customer chooses the new PIN. Using the code revokes existing sessions. The operator does not see the new PIN.

Platform recovery is separate. It stays off until `DUKA_PLATFORM_RECOVERY_CODE` is set. `POST /api/platform/recover` then sets a new PIN for an existing platform phone. Remove the variable after that use.

## Offline operation

IndexedDB holds the device identity, catalogue cache, and outbox. Sync posts each pending sale with its idempotency key, device id, and installation id. A rejected sale stays on the device for a manager. It is not deleted, because the goods may already have left the shop.

## Production deployment

```text
SPRING_PROFILES_ACTIVE=prod
```

Required: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DUKA_JWT_SECRET` (16+ characters). Add Paystack, Daraja, and eTIMS values only when those integrations should leave the unconfigured state. Production does not load the demo catalogue and has no default PIN.

Suggested first deployment: one host for the API and Postgres, HTTPS in front of the API, and the Electron till on the shop PC. CORS must allow the till's origin. Point the packaged app at the API with:

```bash
DUKA_API_URL=https://api.example.com/api
```

Development may keep `http://localhost:4000/api`. Do not bake localhost into a till you hand to a shop. The desktop shell reads `DUKA_API_URL` at launch and passes it to the UI. The variable must include the `/api` prefix.

`DUKA_PRINTER` is the CUPS queue name for the packaged app. Leave it blank and a test print reports that no printer is configured.

More installation and conflict notes are in `docs/PRODUCTION.md`.

## Linux installer

```bash
npm run package:linux
```

This builds:

- `release/Duka-0.3.0.AppImage`
- `release/duka-app_0.3.0_amd64.deb`

The build needs network access to fetch Electron if it is not cached. Packaging was produced in this workspace before; re-run the command on the machine that will ship the installer.

## Environment variables

Names only. Never put real secrets in this file or in git.

Shop and API: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `DUKA_JWT_SECRET`, `DUKA_BOOTSTRAP_PHONE`, `DUKA_BOOTSTRAP_PIN`, `DUKA_ALLOW_REGISTRATION`, `DUKA_STORAGE_PATH`.

Platform: `DUKA_PLATFORM_ADMIN_PHONE`, `DUKA_PLATFORM_ADMIN_PIN`, `DUKA_PLATFORM_RECOVERY_CODE`.

Billing: `DUKA_BILLING_PROVIDER`, `DUKA_BILLING_SECRET_KEY`, `DUKA_BILLING_PUBLIC_KEY`, `DUKA_BILLING_WEBHOOK_SECRET`, `DUKA_BILLING_CALLBACK_URL`, `DUKA_BILLING_WEBHOOK_URL`.

M-Pesa: `MPESA_MODE`, `MPESA_WEBHOOK_SECRET`, `MPESA_CONSUMER_KEY`, `MPESA_CONSUMER_SECRET`, `MPESA_SHORTCODE`, `MPESA_PASSKEY`, `MPESA_CALLBACK_URL`.

eTIMS: `ETIMS_MODE`, `ETIMS_BASE_URL`, `ETIMS_TIN`.

Till: `VITE_API_URL` for Vite, `DUKA_API_URL` for the packaged app, `DUKA_PRINTER` for CUPS.

## Security

- Do not commit `.env` or any file with database passwords, JWT secrets, Paystack secrets, or M-Pesa secrets.
- Change the development PIN and platform PIN before a shared deployment.
- Serve the API over HTTPS.
- Production must not use `DUKA_BILLING_PROVIDER=mock`.
- A customer cannot unlock a paid module by editing the till. Entitlements are read from the API.

## API sketch

Shop routes expect `Authorization: Bearer` from `POST /api/auth/login`. Platform routes use the token from `POST /api/platform/login`.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register` | Create a business in `PENDING_APPROVAL` |
| POST | `/api/auth/login` | Phone + PIN |
| POST | `/api/auth/recovery/request` | Tells the customer SMS recovery is not configured |
| POST | `/api/auth/recovery/complete` | One-time code, then a new PIN |
| GET | `/api/subscription` | Plan, status, source, entitlements, payments |
| POST | `/api/subscription/checkout` | Start Paystack. Requires the account password |
| POST | `/api/subscription/verify` | Ask Paystack whether the reference succeeded |
| POST | `/api/integrations/billing/webhook` | Signed Paystack event |
| POST | `/api/sales` | Checkout. Header `Idempotency-Key` |
| POST | `/api/platform/businesses/{id}/approve` | Super admin approval |
| POST | `/api/platform/businesses/{id}/grants` | Admin or promotional plan activation |
| GET | `/api/platform/support/{id}` | Ticket plus subscription and diagnostics |
