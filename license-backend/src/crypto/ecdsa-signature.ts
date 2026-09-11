const P256_COMPONENT_BYTES = 32;
const MAX_DER_SIGNATURE_BYTES = 72;

/** Strictly converts a DER SEQUENCE(INTEGER r, INTEGER s) P-256 signature to IEEE P1363. */
export function p256DerToP1363(der: Uint8Array): Uint8Array {
  if (der.length < 8 || der.length > MAX_DER_SIGNATURE_BYTES) throw new Error("Invalid ECDSA DER size.");
  let offset = 0;
  if (der[offset++] !== 0x30) throw new Error("ECDSA signature is not a DER sequence.");
  const sequenceLength = readShortLength(der, offset++);
  if (sequenceLength !== der.length - offset) throw new Error("Invalid DER sequence length.");
  const r = readInteger(der, offset);
  offset = r.nextOffset;
  const s = readInteger(der, offset);
  offset = s.nextOffset;
  if (offset !== der.length) throw new Error("Trailing DER signature data.");

  const output = new Uint8Array(P256_COMPONENT_BYTES * 2);
  output.set(r.value, P256_COMPONENT_BYTES - r.value.length);
  output.set(s.value, P256_COMPONENT_BYTES * 2 - s.value.length);
  return output;
}

function readShortLength(input: Uint8Array, offset: number): number {
  if (offset >= input.length) throw new Error("Truncated DER length.");
  const length = input[offset]!;
  if ((length & 0x80) !== 0) throw new Error("Invalid or indefinite DER length.");
  return length;
}

function readInteger(input: Uint8Array, start: number): { value: Uint8Array; nextOffset: number } {
  let offset = start;
  if (offset >= input.length || input[offset++] !== 0x02) throw new Error("Expected DER INTEGER.");
  const length = readShortLength(input, offset++);
  if (length === 0 || offset + length > input.length) throw new Error("Invalid DER INTEGER length.");
  let value = input.slice(offset, offset + length);
  if ((value[0]! & 0x80) !== 0) throw new Error("Negative DER INTEGER.");
  if (value.length > 1 && value[0] === 0) {
    if ((value[1]! & 0x80) === 0) throw new Error("Non-minimal DER INTEGER.");
    value = value.slice(1);
  }
  if (value.length > P256_COMPONENT_BYTES) throw new Error("Oversized P-256 INTEGER.");
  if (value.every((byte) => byte === 0)) throw new Error("Zero ECDSA component.");
  return { value, nextOffset: offset + length };
}
