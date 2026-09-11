import type { AdminLicenseCreated } from "./types";

export class AdminClient {
  constructor(private readonly baseUrl: string, private readonly token: string, private readonly fetchImpl: typeof fetch = fetch) {
    const parsed = new URL(baseUrl);
    if (parsed.protocol !== "https:") throw new Error("Admin API URL must use HTTPS");
  }

  create(maxDevices: number, expiresAtEpochSeconds: number | null): Promise<AdminLicenseCreated> {
    return this.request("POST", "/v1/admin/licenses", { maxDevices, expiresAtEpochSeconds });
  }
  show(licenseId: string): Promise<unknown> { return this.request("GET", `/v1/admin/licenses/${encodeURIComponent(licenseId)}`); }
  devices(licenseId: string): Promise<unknown> { return this.request("GET", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/devices`); }
  revoke(licenseId: string): Promise<unknown> { return this.request("POST", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/revoke`, {}); }
  resetDevice(licenseId: string, deviceId: string): Promise<unknown> {
    return this.request("POST", `/v1/admin/licenses/${encodeURIComponent(licenseId)}/devices/${encodeURIComponent(deviceId)}/reset`, {});
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await this.fetchImpl(`${this.baseUrl.replace(/\/$/u, "")}${path}`, {
      method,
      headers: { Authorization: `Bearer ${this.token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const payload = await response.json().catch(() => null) as { data?: T; error?: { code?: string; message?: string } } | null;
    if (!response.ok || payload?.data === undefined) {
      throw new AdminApiError(response.status, payload?.error?.code ?? "ADMIN_API_ERROR", payload?.error?.message ?? "Admin request failed");
    }
    return payload.data;
  }
}

export class AdminApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message); }
}
