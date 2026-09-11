import { describe, expect, it } from "vitest";
import { p256DerToP1363 } from "../src/crypto/ecdsa-signature";

describe("strict P-256 DER to P1363 conversion", () => {
  it("converts minimal, short, and sign-padded components", () => {
    const minimal = der(bytes(1), bytes(2));
    const output = p256DerToP1363(minimal);
    expect(output).toHaveLength(64);
    expect(output[31]).toBe(1); expect(output[63]).toBe(2);
    const highR = Uint8Array.of(0, 0x80, ...new Uint8Array(31));
    const highS = Uint8Array.of(0, 0x90, ...new Uint8Array(31));
    expect(p256DerToP1363(der(highR, highS)).slice(0, 2)).toEqual(Uint8Array.of(0x80, 0));
    expect(p256DerToP1363(der(highR, highS)).slice(32, 34)).toEqual(Uint8Array.of(0x90, 0));
  });

  it.each([
    ["not a sequence", Uint8Array.of(0x31, 6, 2, 1, 1, 2, 1, 1)],
    ["truncated", Uint8Array.of(0x30, 6, 2, 1, 1)],
    ["indefinite length", Uint8Array.of(0x30, 0x80, 2, 1, 1, 2, 1, 1)],
    ["trailing bytes", Uint8Array.of(...der(bytes(1), bytes(1)), 0)],
    ["missing s", Uint8Array.of(0x30, 3, 2, 1, 1)],
    ["zero length", Uint8Array.of(0x30, 5, 2, 0, 2, 1, 1)],
    ["negative", der(Uint8Array.of(0x80), bytes(1))],
    ["non-minimal", der(Uint8Array.of(0, 1), bytes(1))],
    ["zero", der(Uint8Array.of(0), bytes(1))],
    ["oversized", der(new Uint8Array(33).fill(1), bytes(1))],
  ])("rejects %s", (_name, value) => expect(() => p256DerToP1363(value)).toThrow());
});

function bytes(value: number): Uint8Array { return Uint8Array.of(value); }
function der(r: Uint8Array, s: Uint8Array): Uint8Array {
  return Uint8Array.of(0x30, 4 + r.length + s.length, 0x02, r.length, ...r, 0x02, s.length, ...s);
}
