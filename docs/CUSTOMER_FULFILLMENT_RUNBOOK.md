# DexWorkspaceTouch Customer Fulfillment Runbook

REL-001 provides an operator-side workflow only. It does not change the licensing protocol, production keys, Android application, or backend schema. The licensing backend remains the source of truth for license and device status; local fulfillment records map an order reference to a license ID.

## Security rules

- Set `DWT_LICENSE_ADMIN_API_URL` and `DWT_LICENSE_ADMIN_TOKEN` in the operator process environment. Never pass the admin token as a command-line argument or place it in a record, delivery file, ticket, or source control.
- A plaintext License Key is returned only when a license is created. The backend cannot recover it later.
- `fulfillment-records/` never stores the License Key or its hash. `fulfillment-output/` deliberately contains the customer key and is ignored by Git.
- Deliver `customer-delivery.txt` through an approved private channel, then delete the local plaintext delivery copy when it is no longer needed.
- Do not add customer name, phone, email, address, or payment information to the licensing database. `customerReference` is an optional operator label, not a security identity.
- Back up fulfillment records regularly to operator-controlled storage outside the repository. Do not back up plaintext delivery artifacts.

## Environment

Run from `license-backend` after setting the production admin API URL and token in the current shell. Optional overrides are:

```text
DWT_UPDATE_MANIFEST_URL
DWT_FULFILLMENT_RECORDS_DIR
DWT_FULFILLMENT_OUTPUT_DIR
```

The default release source is the public production update manifest. The CLI rejects a non-HTTPS manifest or APK URL and validates the application ID, version, SHA-256, and production signer field.

## Create and deliver a customer license

```powershell
npm run fulfillment -- create --order ORD-0001 --customer CUSTOMER-0001 --max-devices 1
```

`--customer` and `--expires-at` are optional. Omit expiry for the current perpetual model. More than one device must be explicitly requested. The command:

1. rejects a duplicate non-cancelled order;
2. validates the current production release manifest;
3. creates one license through the existing admin API;
4. stores a secret-free JSON record under `fulfillment-records/`;
5. creates `fulfillment-output/<fulfillmentId>/customer-delivery.txt` containing the exact one-time License Key, APK URL, release version, SHA-256, and customer instructions.

Send the delivery file privately. Then record delivery:

```powershell
npm run fulfillment -- mark-delivered ORD-0001
```

Delete the delivery file after confirmed receipt when local plaintext retention is no longer necessary. The customer is responsible for securely retaining the License Key.

## Customer installation and updates

The generated artifact instructs the customer to download the official APK, allow installation from that download source if Android asks, install DexWorkspaceTouch, open it, and enter the exact License Key. Developer options and ADB are not part of normal customer installation.

For updates, the customer uses **DexWorkspaceTouch → Kiểm tra cập nhật**, downloads the new APK, and installs it over the existing application. The customer must not uninstall first: uninstalling can remove workspaces, local data, device identity, and the stored license token.

## Lookup and support

Official customer support email: `dexworkspacetouch.support@gmail.com`. Ask the customer for the
order reference or customer reference, not a publicly shared License Key.

List local records:

```powershell
npm run fulfillment -- list
```

Show a record and current backend license/device state using a fulfillment ID, order reference, license ID, or customer reference:

```powershell
npm run fulfillment -- show ORD-0001
```

The device view uses only the safe LIC-009 response: device ID, redacted fingerprint, status, and timestamps. Never request or store installation IDs, full device hashes, or public keys.

## Replace a phone or reset a binding

First use `show` to identify the old binding, then reset it:

```powershell
npm run fulfillment -- reset-device ORD-0001 <deviceId-from-safe-device-list>
```

The customer then installs the app on the new phone and enters the same License Key. Do not directly rebind a device and do not ask the operator to handle a public key.

## Lost License Key

The old plaintext key cannot be recovered and there is no key escrow. Verify the order/customer reference through the operator's normal process, then issue a controlled replacement:

```powershell
npm run fulfillment -- replace-license ORD-0001 --order ORD-0001-R1 --customer CUSTOMER-0001 --max-devices 1
```

This creates a new license and delivery artifact, revokes the old license, marks the old record `REPLACED`, and records `replacesLicenseId` / `replacedByLicenseId`. If any step reports a failure, do not rerun blindly: inspect both records and backend state first to avoid issuing another license.

## Refund, cancellation, or revocation

```powershell
npm run fulfillment -- revoke ORD-0001 --status cancelled
```

Omit `--status cancelled` for an ordinary revoke. The backend is changed first; the local record is updated only after the API succeeds. A previously issued signed token on an offline device can remain usable until its signed expiry/offline-grace policy takes effect. Do not promise instantaneous offline revocation.

## Statuses and audit

Operational statuses are `DRAFT`, `LICENSE_CREATED`, `DELIVERED`, `ACTIVE`, `REPLACED`, `REVOKED`, and `CANCELLED`. They are not a duplicate cryptographic truth. For example, `DELIVERED` means only that the operator delivered the customer material.

Each record preserves `createdAt`, `updatedAt`, and append-only-style events including `LICENSE_CREATED`, `DELIVERED`, `DEVICE_RESET`, `LICENSE_REVOKED`, and `LICENSE_REPLACED`. Updates use atomic temporary-file replacement and never remove prior events.

## Failure recovery

- Manifest or backend failure before license creation produces no `LICENSE_CREATED` record.
- If the backend creates a license but local record/delivery generation fails, the CLI explicitly reports that a license was created and provides its license ID. Do not immediately create a second license.
- Because a lost plaintext key cannot be regenerated after the process ends, use the replacement-license workflow after checking backend/local state.
- CLI errors expose only safe HTTP status, stable error code/message, and request ID. They never print the admin credential or Authorization header.

## Backup

Periodically copy `fulfillment-records/` to encrypted, operator-controlled storage outside the repository. Test restore and lookup by order/license ID. Never include `fulfillment-output/`, admin credentials, customer License Keys, private keys, or backend secrets in that backup.
