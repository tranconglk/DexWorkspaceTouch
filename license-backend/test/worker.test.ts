import { SELF } from "cloudflare:test";
import { describe, expect, it } from "vitest";

describe("Worker HTTP foundation", () => {
  it("returns the versioned health contract", async () => {
    const response = await SELF.fetch("https://license.test/v1/health");

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("application/json; charset=utf-8");
    expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(response.headers.get("X-Request-Id")).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    await expect(response.json()).resolves.toEqual({
      ok: true,
      data: { service: "dexworkspacetouch-license", version: 1 },
    });
  });

  it("returns JSON NOT_FOUND for an unknown route", async () => {
    const response = await SELF.fetch("https://license.test/missing");

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      error: { code: "NOT_FOUND", message: "Route not found." },
    });
  });

  it("returns METHOD_NOT_ALLOWED and Allow for a wrong health method", async () => {
    const response = await SELF.fetch("https://license.test/v1/health", { method: "POST" });

    expect(response.status).toBe(405);
    expect(response.headers.get("Allow")).toBe("GET");
    await expect(response.json()).resolves.toMatchObject({
      ok: false,
      error: { code: "METHOD_NOT_ALLOWED" },
    });
  });
});
