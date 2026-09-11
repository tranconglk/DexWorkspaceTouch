import { describe, expect, it, vi } from "vitest";
import worker, { objectKey, type Env } from "../src/index";

const encoder = new TextEncoder();

function r2Object(key: string, value: string, range?: R2Range): R2ObjectBody {
  const bytes = encoder.encode(value);
  return {
    key, version: "v1", size: bytes.length, etag: "etag-value", httpEtag: '"etag-value"',
    checksums: {} as R2Checksums, uploaded: new Date(), storageClass: "Standard", range,
    writeHttpMetadata(headers: Headers) { headers.set("Content-Language", "en"); },
    body: new Blob([bytes]).stream(), bodyUsed: false,
    arrayBuffer: async () => bytes.buffer, bytes: async () => bytes, text: async () => value,
    json: async <T>() => JSON.parse(value) as T, blob: async () => new Blob([bytes]),
  };
}

function env(objects: Record<string, R2ObjectBody>): Env {
  return {
    RELEASES: {
      head: vi.fn(async (key: string) => objects[key] ?? null),
      get: vi.fn(async (key: string, options?: R2GetOptions) => {
        const object = objects[key];
        if (!object) return null;
        if (options?.range instanceof Headers && options.range.has("Range")) {
          return { ...object, range: { offset: 0, length: 4 }, body: new Blob([encoder.encode("abcd")]).stream() } as R2ObjectBody;
        }
        return object;
      }),
    } as unknown as R2Bucket,
  };
}

const apkKey = "releases/1.0.0-beta.4/DexWorkspaceTouch-1.0.0-beta.4-5.apk";

describe("update delivery worker", () => {
  it("serves manifest GET with revalidation headers", async () => {
    const response = await worker.fetch(new Request("https://updates.example/update-manifest.json"), env({ "update-manifest.json": r2Object("update-manifest.json", "{}") }));
    expect(response.status).toBe(200); expect(response.headers.get("Content-Type")).toBe("application/json; charset=utf-8");
    expect(response.headers.get("Cache-Control")).toBe("no-cache"); expect(await response.text()).toBe("{}");
  });

  it("streams APK with immutable headers and ETag", async () => {
    const response = await worker.fetch(new Request(`https://updates.example/${apkKey}`), env({ [apkKey]: r2Object(apkKey, "apk") }));
    expect(response.status).toBe(200); expect(response.headers.get("Content-Type")).toBe("application/vnd.android.package-archive");
    expect(response.headers.get("Cache-Control")).toContain("immutable"); expect(response.headers.get("ETag")).toBe('"etag-value"');
    expect(await response.text()).toBe("apk");
  });

  it("supports HEAD without an object body", async () => {
    const response = await worker.fetch(new Request(`https://updates.example/${apkKey}`, { method: "HEAD" }), env({ [apkKey]: r2Object(apkKey, "apk") }));
    expect(response.status).toBe(200); expect(response.body).toBeNull(); expect(response.headers.get("Content-Length")).toBe("3");
  });

  it("returns 404 for missing objects", async () => {
    expect((await worker.fetch(new Request(`https://updates.example/${apkKey}`), env({}))).status).toBe(404);
  });

  it("rejects wrong methods and exposes no upload or delete route", async () => {
    for (const method of ["POST", "PUT", "DELETE"]) {
      const response = await worker.fetch(new Request("https://updates.example/update-manifest.json", { method }), env({}));
      expect(response.status).toBe(405); expect(response.headers.get("Allow")).toBe("GET, HEAD");
    }
  });

  it("blocks arbitrary keys, listing, traversal, encoded traversal, and empty segments", () => {
    for (const path of ["/", "/secret.json", "/releases", "/releases//x.apk", "/releases/../secret", "/releases/%252e%252e/secret"]) {
      expect(objectKey(new Request(`https://updates.example${path}`))).toBeNull();
    }
  });

  it("handles byte ranges as 206", async () => {
    const response = await worker.fetch(new Request(`https://updates.example/${apkKey}`, { headers: { Range: "bytes=0-3" } }), env({ [apkKey]: r2Object(apkKey, "abcdefgh") }));
    expect(response.status).toBe(206); expect(response.headers.get("Content-Range")).toBe("bytes 0-3/8");
    expect(response.headers.get("Content-Length")).toBe("4"); expect(await response.text()).toBe("abcd");
  });
});
