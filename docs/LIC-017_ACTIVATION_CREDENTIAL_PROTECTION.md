# LIC-017 — Activation Credential Exposure Hardening

## Audit decision

Repository evidence shows LIC-001 through LIC-016 are implemented and covered by Android/backend tests and production runbooks. No protocol milestone is partially implemented. The next smallest security gap is the activation credential surface: the production runbook records that an operator UI diagnostic once exposed a disposable License Key, while the activation text field previously rendered plaintext and the UI state diagnostic included its raw input.

LIC-017 closes only that exposure boundary. It does not change license-key format, activation/proof/refresh protocols, device binding, signed tokens, offline grace, trusted keys, backend configuration, or release signing.

## Controls

- The activation field uses password visual transformation and password keyboard semantics.
- Autocorrect and suggestions are disabled for canonical key input.
- While the activation screen is composed, its Activity window uses Android `FLAG_SECURE` and the root view rejects obscured touches.
- Both protections are restored to their prior values when the activation screen leaves composition; the licensed application is not globally forced into secure-window mode.
- `LicenseGateUiState.ActivationRequired.toString()` always emits `<redacted>` instead of the current input.
- Successful activation retains the existing behavior of replacing the activation state, so the input is removed from UI state.

## Boundaries

The key remains available in memory only as needed to submit activation and support user correction or retry. It is not persisted by the Android client. Password masking is not a defense against a fully compromised device or a malicious input method. Operators must still avoid UI hierarchy dumps during real customer activation and must never paste customer keys into logs or support tickets.

No backend or Cloudflare deployment is required. No Car feature is changed.
