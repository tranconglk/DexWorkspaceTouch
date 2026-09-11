import { describe, expect, it } from "vitest";
import { ApplicationIdentityPolicy } from "../src/license/application-identity-policy";

const PACKAGE = "com.trancong.dexworkspacetouch";
const FIRST = "a".repeat(64);
const SECOND = "b".repeat(64);

describe("production application identity policy", () => {
  it("accepts the configured package and any allowlisted certificate", () => {
    const policy = ApplicationIdentityPolicy.create(PACKAGE, ` ${FIRST.toUpperCase()} ,${SECOND},${FIRST}`);
    expect(policy.allowedCertificateCount).toBe(2);
    expect(policy.allows(application(PACKAGE, FIRST))).toBe(true);
    expect(policy.allows(application(PACKAGE, SECOND.toUpperCase()))).toBe(true);
  });

  it("rejects wrong package, missing certificate, and unknown certificate", () => {
    const policy = ApplicationIdentityPolicy.create(PACKAGE, FIRST);
    expect(policy.allows(application("com.example.resigned", FIRST))).toBe(false);
    expect(policy.allows(application(PACKAGE, null))).toBe(false);
    expect(policy.allows(application(PACKAGE, SECOND))).toBe(false);
  });

  it.each([undefined, "", " ", `${FIRST},`, "not-a-digest", `${FIRST}, ,${SECOND}`])(
    "fails closed for unavailable or malformed config %s", (configured) => {
      expect(() => ApplicationIdentityPolicy.create(PACKAGE, configured)).toThrow();
    },
  );

  it("fails closed for an invalid configured package", () => {
    expect(() => ApplicationIdentityPolicy.create("", FIRST)).toThrow();
    expect(() => ApplicationIdentityPolicy.create(" com.example.app", FIRST)).toThrow();
  });
});

function application(packageName: string, certificate: string | null) {
  return { packageName, versionName: "test", versionCode: 1, signingCertificateSha256: certificate };
}
