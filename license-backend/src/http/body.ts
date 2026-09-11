export type JsonBodyResult =
  | { readonly ok: true; readonly value: unknown }
  | { readonly ok: false; readonly code: "UNSUPPORTED_MEDIA_TYPE" | "INVALID_JSON" | "PAYLOAD_TOO_LARGE" };

export const MAX_JSON_BODY_BYTES = 32 * 1024;

export async function parseJsonBody(request: Request): Promise<JsonBodyResult> {
  const mediaType = request.headers.get("Content-Type")?.split(";", 1)[0]?.trim().toLowerCase();
  if (mediaType !== "application/json") {
    return { ok: false, code: "UNSUPPORTED_MEDIA_TYPE" };
  }
  const declaredLength = request.headers.get("Content-Length");
  if (declaredLength !== null && /^\d+$/.test(declaredLength) && Number(declaredLength) > MAX_JSON_BODY_BYTES) {
    return { ok: false, code: "PAYLOAD_TOO_LARGE" };
  }
  try {
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength > MAX_JSON_BODY_BYTES) return { ok: false, code: "PAYLOAD_TOO_LARGE" };
    return { ok: true, value: JSON.parse(new TextDecoder().decode(bytes)) };
  } catch {
    return { ok: false, code: "INVALID_JSON" };
  }
}
