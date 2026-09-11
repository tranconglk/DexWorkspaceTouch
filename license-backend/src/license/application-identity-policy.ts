import type { BoundActivationRequest } from "./types";

const SHA256_HEX = /^[0-9a-f]{64}$/;
const PACKAGE_NAME = /^[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

export class ApplicationIdentityPolicy {
  private constructor(
    private readonly packageName: string,
    private readonly allowedCertificates: ReadonlySet<string>,
  ) {}

  static create(packageName: string | undefined, configuredCertificates: string | undefined): ApplicationIdentityPolicy {
    if (typeof packageName !== "string" || !PACKAGE_NAME.test(packageName) || packageName.trim() !== packageName) {
      throw new Error("Production Android package configuration is invalid.");
    }
    if (typeof configuredCertificates !== "string" || configuredCertificates.length === 0) {
      throw new Error("Android signing-certificate allowlist is unavailable.");
    }
    const entries = configuredCertificates.split(",");
    if (entries.some((entry) => entry.trim().length === 0)) {
      throw new Error("Android signing-certificate allowlist contains an empty entry.");
    }
    const normalized = entries.map((entry) => entry.trim().toLowerCase());
    if (normalized.some((entry) => !SHA256_HEX.test(entry))) {
      throw new Error("Android signing-certificate allowlist is malformed.");
    }
    return new ApplicationIdentityPolicy(packageName, new Set(normalized));
  }

  allows(application: BoundActivationRequest["application"]): boolean {
    const certificate = application.signingCertificateSha256;
    return application.packageName === this.packageName && typeof certificate === "string" &&
      SHA256_HEX.test(certificate.toLowerCase()) && this.allowedCertificates.has(certificate.toLowerCase());
  }

  get allowedCertificateCount(): number { return this.allowedCertificates.size; }
}
