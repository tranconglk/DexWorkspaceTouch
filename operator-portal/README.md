# DexWorkspaceTouch Mobile Operator Portal (REL-002)

Dedicated Cloudflare Worker: `dexworkspacetouch-operator`.

## Production identifiers (non-secret)

- Operator URL: `https://dexworkspacetouch-operator.dex-backend.workers.dev`
- Cloudflare Access team domain: `dexworkspacetouch.cloudflareaccess.com`
- Cloudflare Access application AUD: `58fb2050630759d33327cb4a5eb7119c8049fa4b404155de3cbf5692d76c1105`
- Explicit operator allowlist: `tranconglk@gmail.com`
- Operational D1: `dexworkspacetouch-fulfillment`
- D1 database ID: `97fed380-f04e-4b61-9cd6-abde3ad59240`
- Internal release Worker binding: `RELEASES` -> `dexworkspacetouch-updates`
- Internal license Admin Worker binding: `LICENSE_ADMIN` -> `dexworkspacetouch-license-production`

The AUD, team domain, Worker URL, email allowlist, Worker names, and D1 IDs above are identifiers,
not credentials. Never add the Access JWT/cookie, admin token, plaintext License Key, private signing
key, or Cloudflare API credentials to this file.

The portal stores only non-secret order/fulfillment metadata in the separate D1 database
`dexworkspacetouch-fulfillment`. It calls the existing production licensing Admin API over HTTPS;
it never binds or queries the licensing D1 database directly. Existing desktop CLIs remain unchanged.

## Security boundary

1. Create a Cloudflare Access self-hosted application covering the entire Operator Worker hostname.
2. Add an Allow policy containing only explicit operator email accounts. Email OTP or an existing
   identity provider can be used by Access.
3. Do not create a Bypass policy for `/api/*`, the Worker hostname, or `workers.dev`.
4. Configure the Worker variables `CF_ACCESS_TEAM_DOMAIN`, `CF_ACCESS_AUD`, and
   `OPERATOR_EMAIL_ALLOWLIST`. The Worker verifies the Access JWT signature, issuer, audience,
   expiry, and allowlisted email on every request; the Access policy remains the first boundary.
5. Store the production licensing admin credential only as Worker secret:

```powershell
npx wrangler secret put LICENSE_ADMIN_TOKEN
```

The browser never receives this token. Mutation routes require POST, exact same-origin `Origin`, and
the portal-only `X-DWT-CSRF: 1` header. No CORS response is emitted.

## Initial setup

```powershell
cd operator-portal
npm install
npx wrangler d1 create dexworkspacetouch-fulfillment
```

Copy the returned D1 database ID into `wrangler.jsonc`, replace the three Access placeholders, then:

```powershell
npm run db:migrate:remote
npx wrangler secret put LICENSE_ADMIN_TOKEN
npm run check
npm run deploy
```

After deploy, configure Cloudflare Access for the actual Worker URL before performing any production
operation. An absent, invalid, expired, wrong-audience, or non-allowlisted Access assertion is also
rejected by the Worker.

## Production operator runbook

### Verify and deploy portal code

From `operator-portal/`:

```powershell
npm.cmd ci
npm.cmd run check
npx.cmd wrangler deploy
```

An ordinary deploy reuses the existing `LICENSE_ADMIN_TOKEN` Worker secret. Do not put its value on
the command line or in `wrangler.jsonc`. To rotate the secret, use the interactive command below and
paste it only into Wrangler's secret prompt:

```powershell
npx.cmd wrangler secret put LICENSE_ADMIN_TOKEN
```

After deployment, verify an unauthenticated request redirects to Cloudflare Access rather than
serving the portal directly:

```powershell
curl.exe -sS -D - -o NUL https://dexworkspacetouch-operator.dex-backend.workers.dev/
```

Expected: HTTP `302` with a `Location` under
`https://dexworkspacetouch.cloudflareaccess.com/cdn-cgi/access/login/`. Sign in only through that
page using an explicitly allowlisted operator account.

### Fulfill a customer order

1. Open the Operator URL and authenticate through Cloudflare Access.
2. Enter a unique order reference, optional customer reference, device limit, and optional expiry.
3. Select **Tạo license** once. Do not retry blindly after a partial-failure/recovery message.
4. Copy, share, or download the one-time delivery text before navigating away.
5. Deliver the APK URL, APK SHA-256, and plaintext License Key only to the intended customer.
6. Select **Đã giao** after delivery is confirmed.
7. Search by order, fulfillment, license, or customer reference for later support.
8. Use device reset for an authorized phone replacement; use revoke for refund/abuse termination;
   use replacement when an undelivered key has been lost.

The portal cannot recover an old plaintext key. Delete local delivery files and clear clipboard
contents after delivery. Never paste a customer key into logs, issue trackers, chat, or diagnostics.

### Production health and audit

Before customer operations, confirm the portal loads after Access login and the production update
manifest is current. For a release or infrastructure change, execute the disposable smoke flow below
and finish by revoking its license. D1 inspection must remain metadata-only; never add key material to
the operational schema.

## Production smoke

On an S23/mobile browser behind Access:

1. Sign in with an explicitly allowed operator account.
2. Create a disposable order such as `REL002-SMOKE-<date>`.
3. Copy/share or download the one-time delivery content.
4. Open the saved fulfillment detail and confirm the key is no longer present.
5. Mark it delivered.
6. Revoke it and confirm the backend and persistent fulfillment status.
7. Inspect D1 and Worker logs only for non-secret metadata; never paste the key into diagnostics.

No S23 license activation is required for this smoke. Delete the browser/download plaintext delivery
copy when it is no longer needed. Do not silently import existing `fulfillment-records/`; migration is
a separate future operation.

## Failure recovery

- If license creation succeeds but D1 persistence fails, the response reports
  `PERSISTENCE_FAILED_AFTER_LICENSE_CREATE` and the created `licenseId`. Do not retry create blindly.
- Replacement revokes the old backend license first. If creating the new license fails, the response
  reports `OLD_LICENSE_REVOKED_NEW_LICENSE_NOT_CREATED`.
- If the new replacement license exists but its operational record/link cannot be completed, the
  response reports `NEW_LICENSE_CREATED_RECORD_INCOMPLETE` with safe license IDs. Inspect backend and
  D1 state before any manual recovery.

Plaintext keys are never persisted in D1 and cannot be regenerated after the one-time browser result
is lost. Use the controlled replacement workflow when a key was not delivered.
