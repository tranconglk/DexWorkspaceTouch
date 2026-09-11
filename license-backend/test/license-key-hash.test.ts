import { describe, expect, it } from "vitest";
import { hashLicenseKey } from "../src/license/license-key-hash";

const PEPPER = "lic-004-unit-test-pepper-not-for-production";

describe("license-key HMAC lookup hash", () => {
  it("matches the fixed HMAC-SHA-256 vector", async () => {
    await expect(hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER)).resolves.toBe(
      "c8de0ab77c870e579590976a1563e53c31030503dbd51ff45c418f25e003fc36",
    );
  });

  it("is deterministic for the same canonical key and pepper", async () => {
    expect(await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER)).toBe(
      await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER),
    );
  });

  it("changes when the key changes", async () => {
    expect(await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER)).not.toBe(
      await hashLicenseKey("APP-7K3M-9QTX-2PL9", PEPPER),
    );
  });

  it("changes when the pepper changes", async () => {
    expect(await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER)).not.toBe(
      await hashLicenseKey("APP-7K3M-9QTX-2PL8", "different-test-only-pepper"),
    );
  });

  it("normalizes canonical-equivalent input", async () => {
    expect(await hashLicenseKey("  app-7k3m-9qtx-2pl8  ", PEPPER)).toBe(
      await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER),
    );
  });

  it("returns lowercase 64-character hex without plaintext", async () => {
    const hash = await hashLicenseKey("APP-7K3M-9QTX-2PL8", PEPPER);
    expect(hash).toMatch(/^[0-9a-f]{64}$/);
    expect(hash).not.toContain("APP-7K3M-9QTX-2PL8");
  });

  it("rejects invalid keys and empty pepper", async () => {
    await expect(hashLicenseKey("not-a-key", PEPPER)).rejects.toThrow();
    await expect(hashLicenseKey("APP-7K3M-9QTX-2PL8", " ")).rejects.toThrow();
  });
});
