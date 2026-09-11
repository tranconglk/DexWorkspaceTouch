import { describe, expect, it } from "vitest";
import { MAX_JSON_BODY_BYTES, parseJsonBody } from "../src/http/body";

describe("bounded JSON request bodies", () => {
  it("accepts a JSON body within the limit", async () => {
    await expect(parseJsonBody(request("{}"))).resolves.toEqual({ ok: true, value: {} });
  });
  it("rejects declared and actual bodies over the limit", async () => {
    const declared = new Request("https://license.test", { method: "POST",
      headers: { "Content-Type": "application/json", "Content-Length": String(MAX_JSON_BODY_BYTES + 1) }, body: "{}" });
    expect(await parseJsonBody(declared)).toEqual({ ok: false, code: "PAYLOAD_TOO_LARGE" });
    expect(await parseJsonBody(request(JSON.stringify({ value: "x".repeat(MAX_JSON_BODY_BYTES) })))).toEqual({ ok: false, code: "PAYLOAD_TOO_LARGE" });
  });
});

function request(body: string): Request {
  return new Request("https://license.test", { method: "POST", headers: { "Content-Type": "application/json" }, body });
}
