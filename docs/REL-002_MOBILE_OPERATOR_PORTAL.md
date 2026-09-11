# REL-002 — Mobile Operator Portal

REL-002 adds the dedicated `operator-portal/` Cloudflare Worker without modifying the Android app,
the production licensing Worker, the update-delivery Worker, or the cryptographic protocol.

## Components

- Worker: `dexworkspacetouch-operator`
- Operator URL: `https://dexworkspacetouch-operator.dex-backend.workers.dev`
- Access team domain: `dexworkspacetouch.cloudflareaccess.com`
- Access application AUD: `58fb2050630759d33327cb4a5eb7119c8049fa4b404155de3cbf5692d76c1105`
- Operator allowlist: `tranconglk@gmail.com`
- Operational D1: `dexworkspacetouch-fulfillment`
- UI/API: mobile-first, same-origin, self-contained Worker response
- Authentication: Cloudflare Access plus server-side Access JWT validation and explicit email allowlist
- Licensing integration: existing production `/v1/admin/licenses` API via server-side Worker secret
- Release integration: validated production `update-manifest.json`

The portal supports create, search/list, detail/device status, mark-delivered, device reset, revoke,
and replacement. Create/replace responses expose a License Key exactly once with `Cache-Control:
no-store`; fulfillment rows/events contain no key or key hash. The delivery file is generated only in
browser memory for copy, Web Share, or download.

Existing local `fulfillment-records/` remain untouched and are not uploaded. The existing
`license-backend/scripts/fulfillment.mjs` and `admin.mjs` remain emergency/desktop tooling.

The portal uses Cloudflare service bindings for same-account Worker calls: `RELEASES` targets
`dexworkspacetouch-updates`, and `LICENSE_ADMIN` targets `dexworkspacetouch-license-production`.
This avoids same-account `workers.dev` subrequest failures while retaining the existing manifest and
Admin API implementations.

Deployment, fulfillment, recovery, security, and production smoke instructions are maintained in
`operator-portal/README.md`. The Access AUD, team domain, Worker URL, allowlisted email, Worker names,
and D1 identifier are non-secret operational identifiers. Access JWTs/cookies, Worker admin tokens,
plaintext customer License Keys, private signing keys, and Cloudflare credentials must never be
committed or copied into the runbook.
