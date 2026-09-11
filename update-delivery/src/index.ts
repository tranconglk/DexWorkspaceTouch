export interface Env {
  RELEASES: R2Bucket;
}

const MANIFEST_KEY = "update-manifest.json";
const RELEASE_PATH = /^\/releases\/([0-9A-Za-z][0-9A-Za-z.-]{0,63})\/(DexWorkspaceTouch-[0-9A-Za-z][0-9A-Za-z.-]{0,100}-[1-9][0-9]*\.apk)$/;

function objectKey(request: Request): string | null {
  const rawPath = new URL(request.url).pathname;
  let path: string;
  try {
    path = decodeURIComponent(rawPath);
  } catch {
    return null;
  }
  if (path !== rawPath || /[\u0000-\u001f\u007f]/.test(path) || path.includes("..") || path.includes("//")) {
    return null;
  }
  if (path === `/${MANIFEST_KEY}`) return MANIFEST_KEY;
  const match = RELEASE_PATH.exec(path);
  return match ? path.slice(1) : null;
}

function commonHeaders(object: R2Object, isManifest: boolean): Headers {
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("ETag", object.httpEtag);
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("Accept-Ranges", "bytes");
  if (isManifest) {
    headers.set("Content-Type", "application/json; charset=utf-8");
    headers.set("Cache-Control", "no-cache");
  } else {
    headers.set("Content-Type", "application/vnd.android.package-archive");
    headers.set("Cache-Control", "public, max-age=31536000, immutable");
    headers.set("Content-Disposition", `attachment; filename="${object.key.split("/").at(-1) ?? "DexWorkspaceTouch.apk"}"`);
  }
  return headers;
}

function setLengthAndRange(headers: Headers, object: R2Object, requestedRange: boolean): number {
  const range = object.range;
  if (requestedRange && range && "offset" in range && range.offset !== undefined && range.length !== undefined) {
    headers.set("Content-Length", String(range.length));
    headers.set("Content-Range", `bytes ${range.offset}-${range.offset + range.length - 1}/${object.size}`);
    return 206;
  }
  headers.set("Content-Length", String(object.size));
  return 200;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }
    const key = objectKey(request);
    if (!key) return new Response("Not Found", { status: 404 });

    const isManifest = key === MANIFEST_KEY;
    if (request.method === "HEAD") {
      const object = await env.RELEASES.head(key);
      if (!object) return new Response("Not Found", { status: 404 });
      const headers = commonHeaders(object, isManifest);
      headers.set("Content-Length", String(object.size));
      return new Response(null, { status: 200, headers });
    }

    const requestedRange = !isManifest && request.headers.has("Range");
    const object = await env.RELEASES.get(key, {
      range: requestedRange ? request.headers : undefined,
      onlyIf: request.headers,
    });
    if (!object) return new Response("Not Found", { status: 404 });
    if (!("body" in object)) {
      const headers = commonHeaders(object, isManifest);
      return new Response(null, { status: request.headers.has("If-None-Match") ? 304 : 412, headers });
    }
    const headers = commonHeaders(object, isManifest);
    const status = setLengthAndRange(headers, object, requestedRange);
    return new Response(object.body, { status, headers });
  },
};

export { objectKey };
