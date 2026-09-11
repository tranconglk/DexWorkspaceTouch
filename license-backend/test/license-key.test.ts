import { describe, expect, it } from "vitest";
import vectors from "./fixtures/license-key-vectors.json";
import {
  canonicalizeLicenseKey,
  redactLicenseKey,
  validateLicenseKey,
} from "../src/license/license-key";

describe("Android-compatible license key contract", () => {
  for (const vector of vectors) {
    it(`validates ${JSON.stringify(vector.input)}`, () => {
      const result = validateLicenseKey(vector.input);
      expect(result).toEqual({ canonical: vector.canonical, valid: vector.valid });

      if (vector.valid) {
        expect(canonicalizeLicenseKey(vector.input)).toBe(vector.canonical);
        expect(redactLicenseKey(vector.input)).toBe(vector.redacted);
        expect(redactLicenseKey(vector.input)).not.toContain(vector.canonical);
      } else {
        expect(() => canonicalizeLicenseKey(vector.input)).toThrow("License key format is invalid.");
      }
    });
  }
});
