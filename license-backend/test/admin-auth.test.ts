import { describe, expect, it } from "vitest";
import { isAdminAuthorized } from "../src/admin/admin-auth";
import type { Env } from "../src/env";

function request(value?: string): Request {
  return new Request("https://license.test/v1/admin/licenses", value === undefined ? {} : { headers: { Authorization: value } });
}
function environment(token?: string): Env { return { LICENSE_ADMIN_TOKEN: token } as Env; }

describe("admin authentication", () => {
  it("accepts exactly one correct Bearer credential", async () => {
    await expect(isAdminAuthorized(request("Bearer secret"), environment("secret"))).resolves.toBe(true);
  });
  it.each([undefined, "", "Basic secret", "Bearer", "Bearer secret extra", "Bearer wrong"])("rejects malformed or wrong credential %s", async (header) => {
    await expect(isAdminAuthorized(request(header), environment("secret"))).resolves.toBe(false);
  });
  it("fails closed when the server secret is missing or empty", async () => {
    await expect(isAdminAuthorized(request("Bearer secret"), environment())).resolves.toBe(false);
    await expect(isAdminAuthorized(request("Bearer "), environment(""))).resolves.toBe(false);
  });
});
