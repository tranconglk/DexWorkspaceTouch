import { describe, expect, it } from "vitest";
import { generateLicenseKey } from "../src/admin/license-key-generator";
import { validateLicenseKey } from "../src/license/license-key";

describe("admin license key generation", () => {
  it("creates a canonical DWT key from cryptographic bytes", () => {
    const key = generateLicenseKey(() => Uint8Array.from({ length: 12 }, (_, index) => index));
    expect(key).toMatch(/^DWT-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    expect(validateLicenseKey(key).valid).toBe(true);
  });
  it("does not return deterministic production keys", () => {
    expect(generateLicenseKey()).not.toBe(generateLicenseKey());
  });
});
