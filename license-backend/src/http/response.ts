export interface ApiErrorBody {
  readonly code: string;
  readonly message: string;
}

export type ApiEnvelope<T> =
  | { readonly ok: true; readonly data: T }
  | { readonly ok: false; readonly error: ApiErrorBody };

const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
} as const;

export function jsonSuccess<T>(data: T, status = 200, headers?: HeadersInit): Response {
  return jsonResponse({ ok: true, data }, status, headers);
}

export function jsonError(
  status: number,
  code: string,
  message: string,
  headers?: HeadersInit,
): Response {
  return jsonResponse({ ok: false, error: { code, message } }, status, headers);
}

function jsonResponse<T>(body: ApiEnvelope<T>, status: number, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...JSON_HEADERS, ...headers },
  });
}
