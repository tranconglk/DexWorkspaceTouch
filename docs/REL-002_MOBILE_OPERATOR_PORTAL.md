# REL-002 — Mobile Operator Portal

REL-002 adds the dedicated `operator-portal/` Cloudflare Worker without modifying the Android app,
the production licensing Worker, the update-delivery Worker, or the cryptographic protocol.

## Components

- Worker: `dexworkspacetouch-operator`
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

Deployment and production smoke instructions are in `operator-portal/README.md`. The Access
application, allowed identity, D1 database ID, Worker secret, deployment, and production smoke are
operator-controlled production steps and cannot be considered complete until configured in the
Cloudflare account.
