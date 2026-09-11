# DexWorkspaceTouch Mobile Operator Portal (REL-002)

Dedicated Cloudflare Worker: `dexworkspacetouch-operator`.

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
