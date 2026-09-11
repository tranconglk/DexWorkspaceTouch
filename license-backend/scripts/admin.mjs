import { pathToFileURL } from "node:url";

const [command, ...args] = process.argv.slice(2);

export function parseCommand(commandName, values) {
  switch (commandName) {
    case "create": {
      let maxDevices = 1;
      let expiresAtEpochSeconds = null;
      for (let index = 0; index < values.length; index += 2) {
        const option = values[index];
        const raw = values[index + 1];
        if (raw === undefined) throw new Error(`Missing value for ${option}.`);
        if (option === "--max-devices") maxDevices = integer(raw, option);
        else if (option === "--expires-at") expiresAtEpochSeconds = integer(raw, option);
        else throw new Error(`Unknown option: ${option}`);
      }
      return { method: "POST", path: "/v1/admin/licenses", body: { maxDevices, expiresAtEpochSeconds } };
    }
    case "list": return { method: "GET", path: "/v1/admin/licenses" };
    case "show": return idCommand("GET", values, (id) => `/v1/admin/licenses/${encodeURIComponent(id)}`);
    case "revoke": return idCommand("POST", values, (id) => `/v1/admin/licenses/${encodeURIComponent(id)}/revoke`, {});
    case "devices": return idCommand("GET", values, (id) => `/v1/admin/licenses/${encodeURIComponent(id)}/devices`);
    case "reset-device": {
      if (values.length !== 2) throw new Error("Usage: reset-device <licenseId> <deviceId>");
      return { method: "POST", path: `/v1/admin/licenses/${encodeURIComponent(values[0])}/devices/${encodeURIComponent(values[1])}/reset`, body: {} };
    }
    default: throw new Error("Commands: create, list, show, revoke, devices, reset-device");
  }
}

export function readConfiguration(environment) {
  const apiUrl = environment.DWT_LICENSE_ADMIN_API_URL;
  const token = environment.DWT_LICENSE_ADMIN_TOKEN;
  if (!apiUrl || !token) throw new Error("DWT_LICENSE_ADMIN_API_URL and DWT_LICENSE_ADMIN_TOKEN are required.");
  const url = new URL(apiUrl);
  const local = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  if (url.protocol !== "https:" && !(local && url.protocol === "http:")) throw new Error("Admin API URL must use HTTPS (HTTP is allowed only for localhost). ");
  return { apiUrl: url.toString().replace(/\/$/, ""), token };
}

export function formatError(status, payload, requestId, retryAfter = null) {
  const code = payload?.error?.code ?? "UNKNOWN_ERROR";
  const message = payload?.error?.message ?? "Request failed.";
  const retry = status === 429 && retryAfter ? ` Retry-After: ${retryAfter}.` : "";
  return `HTTP ${status} ${code}: ${message}${retry}${requestId ? ` (request ${requestId})` : ""}`;
}

async function main() {
  const operation = parseCommand(command, args);
  const config = readConfiguration(process.env);
  const response = await fetch(`${config.apiUrl}${operation.path}`, {
    method: operation.method,
    headers: { Authorization: `Bearer ${config.token}`, ...(operation.body ? { "Content-Type": "application/json" } : {}) },
    body: operation.body ? JSON.stringify(operation.body) : undefined,
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok) throw new Error(formatError(response.status, payload, response.headers.get("X-Request-Id"),
    safeRetryAfter(response.headers.get("Retry-After"))));
  if (command === "create") {
    const value = payload.data;
    console.log(`License ID: ${value.licenseId}`);
    console.log(`License Key: ${value.licenseKey}`);
    console.log(`Max devices: ${value.maxDevices}`);
    console.log(`Expires: ${value.expiresAtEpochSeconds ?? "never"}`);
    console.log("Store the License Key securely; it cannot be retrieved later.");
  } else console.log(JSON.stringify(payload.data, null, 2));
}

function idCommand(method, values, path, body) {
  if (values.length !== 1) throw new Error(`Expected exactly one licenseId.`);
  return { method, path: path(values[0]), ...(body === undefined ? {} : { body }) };
}
function integer(raw, option) { const value = Number(raw); if (!Number.isSafeInteger(value)) throw new Error(`${option} must be an integer.`); return value; }
export function safeRetryAfter(value) { return typeof value === "string" && /^\d{1,5}$/.test(value) ? value : null; }

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => { console.error(error instanceof Error ? error.message : "Admin command failed."); process.exitCode = 1; });
}
