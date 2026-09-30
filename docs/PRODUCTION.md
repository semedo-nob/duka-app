# Duka production notes

This file is the installation, sync, and acceptance record. It does not claim that hardware or KRA/Daraja credentials have been verified.

## Accounts

A business, its owner, staff, devices, and sessions are separate records. A new registration is `PENDING_APPROVAL` until a super admin approves it. Approval starts a 14-day Core trial. Paid modules stay locked until Paystack verifies a charge. The owner invites staff with a one-time code. The staff member chooses the PIN. That code is stored only as a hash. Production does not load the demo catalogue in `db/dev`. A fresh production database can accept the first owner registration when no user exists yet. Later registrations stay closed unless `DUKA_ALLOW_REGISTRATION` is turned on. SMS recovery is not configured: the owner issues a recovery code, or platform support issues one for the owner. The code expires, works once, and revokes sessions. Closing a business keeps the financial history.

The first platform super admin is created at startup when `platform_admins` is empty and `DUKA_PLATFORM_ADMIN_PHONE` plus `DUKA_PLATFORM_ADMIN_PIN` are set. Phone `0700000000` and PIN `123456` are refused unless the Spring profile is `dev` or `test`. If that login is lost, set `DUKA_PLATFORM_RECOVERY_CODE`, call `POST /api/platform/recover` with the platform phone, that code, and a new PIN of at least 8 characters, then unset the variable. The reset is audited. Platform operators never see a customer PIN.

## Conflict policy

Sales are append-only. Each device sale has its own idempotency key, device id, installation id, and client receipt number. The central API stores that sale once. A repeated sync returns the same sale and does not move stock again.

Stock is a ledger. Devices submit sale events. They do not replace the central quantity with a number from the till. While a sale is waiting to sync, that device keeps its own reduced quantity for the products on that sale. Other products take the central catalogue. When the server rejects a sale, the device copy stays for a manager. It is not deleted and stock is not silently put back, because the goods may already have left the shop.

Two managers editing the same product are last-write-wins on the server, with an audit event. That is not a merge. M-Pesa stock moves only after a verified callback. eTIMS stays pending until a real KRA adapter accepts the invoice. OCR never posts stock by itself.

## What the customer receives

The customer package is a desktop window (Electron) around the POS, plus a separately hosted Spring Boot API and PostgreSQL. The till keeps its own IndexedDB database in the Chromium profile under Electron `userData` (on Linux, `~/.config/Duka`). The customer does not install Node, Gradle, or Postgres on the till.

`npm run package:linux` builds a Vite bundle and asks electron-builder for an AppImage and a `.deb`. This workspace produced `release/Duka-0.3.0.AppImage` and `release/duka-app_0.3.0_amd64.deb`. The packaged app serves that bundle on `127.0.0.1` only. `DUKA_API_URL` must point at the hosted API, for example `https://api.example.com/api`. Development still uses `npm run desktop`, which loads Vite only when `DUKA_DEV=1`.

Uninstalling the `.deb` removes the application files. It does not delete `~/.config/Duka`, so local sales and the outbox remain until that directory is removed. There is no automatic update channel. Replace the AppImage or reinstall the `.deb`, leave the user-data directory in place, and start again so pending sales continue.

First launch, once packaged:

1. Open Duka.
2. The device creates a durable device id and installation id.
3. The owner signs in online once. The PIN is stored only as a PBKDF2 verifier so the same person can unlock the till offline later.
4. The catalogue is saved on the device.
5. Set `DUKA_PRINTER` to a CUPS queue for thermal printing. Without it, a test print reports that no printer is configured. The browser print dialog, when used outside the desktop bridge, does not claim the printer finished.

## Updates

Application version is 0.3.0. The local database is schema 3. Opening a newer schema adds stores and does not delete products, sales, or the outbox. Central Flyway migrations are additive through `V8__admin_grant_support_context.sql`. Do not run `docker compose down -v`. An update must stop new checkouts, leave the IndexedDB files in the app profile, and start again so pending sales continue. There is no automatic backup tool yet.

Platform operators sign in at `/platform` with a platform PIN. That login is not a shop owner. Paid modules unlock only after Paystack verifies the transaction (`GET /transaction/verify/:reference`) or a `charge.success` webhook whose `x-paystack-signature` matches `DUKA_BILLING_SECRET_KEY` (or `DUKA_BILLING_WEBHOOK_SECRET` when set). Kenya M-Pesa subscriptions use Paystack's `POST /charge` mobile-money call with provider `mpesa`. Checkout without those variables returns 503 and leaves the module locked. A repeated webhook does not create a second payment.

`DUKA_BILLING_PROVIDER=mock` is refused when the Spring profile is `prod`. A super admin or billing admin can activate a plan from Business 360. That record uses source `ADMIN_GRANT` or `PROMOTIONAL` and does not insert a Paystack payment. Support admins cannot grant a plan.

## Acceptance checklist

Run this on a machine that will be the till. Mark each line pass or fail. Do not mark a line pass from a mock response.

1. Install or launch the packaged app. Do not start Vite for this test.
2. Owner login online.
3. Disconnect the network. Unlock with the same PIN. Confirm the catalogue is still there.
4. Scan a barcode with a USB wedge scanner (fast digits and Enter). The product is added with no network.
5. Complete a cash sale. A local receipt number is shown before any server id.
6. Print. Record whether CUPS accepted the job or only the system dialog opened.
7. Quit the app and start it again. The sale is still in Diagnostics.
8. Restore the network. Diagnostics shows the sale synced once. The central `sales` row has the same idempotency key and device id. Stock moved once.
9. Repeat the sync. Stock does not move again.
10. M-Pesa stays unpaid until a verified callback. Sandbox confirmation is not a production payment.
11. eTIMS shows pending. Nothing is marked accepted.
12. Unknown barcode does not create a product for a cashier.

## External blockers

- Daraja production credentials and the live callback contract
- KRA eTIMS credentials and the official request contract
- Paystack secret key, webhook signature key, and a public callback URL (`DUKA_BILLING_SECRET_KEY`, `DUKA_BILLING_CALLBACK_URL`)
- A packaged AppImage or `.deb` has to be produced with `npm run package:linux` on a machine that can download Electron
- A named thermal printer and a USB scanner were not attached during this change
- Hosting, TLS, and a production JWT secret are operator tasks
