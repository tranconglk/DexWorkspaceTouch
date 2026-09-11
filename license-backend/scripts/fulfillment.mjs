import path from "node:path";
import { pathToFileURL } from "node:url";
import { AdminClient, DEFAULT_MANIFEST_URL, FulfillmentService, FulfillmentStore } from "./fulfillment-core.mjs";

export function parseArguments(values) {
  const [command, ...rest] = values;
  const positional = [];
  const options = {};
  for (let index = 0; index < rest.length; index++) {
    const value = rest[index];
    if (!value.startsWith("--")) { positional.push(value); continue; }
    const next = rest[++index];
    if (next === undefined) throw new Error(`Missing value for ${value}.`);
    options[value.slice(2)] = next;
  }
  return { command, positional, options };
}

export async function run(values, environment = process.env, output = console) {
  const parsed = parseArguments(values);
  const root = path.resolve(import.meta.dirname, "../..");
  const store = new FulfillmentStore(environment.DWT_FULFILLMENT_RECORDS_DIR ?? path.join(root, "fulfillment-records"));
  if (parsed.command === "list") {
    output.log(JSON.stringify(await store.list(), null, 2)); return;
  }
  const admin = new AdminClient({ apiUrl: environment.DWT_LICENSE_ADMIN_API_URL, token: environment.DWT_LICENSE_ADMIN_TOKEN });
  const service = new FulfillmentService({ admin, store,
    outputDirectory: environment.DWT_FULFILLMENT_OUTPUT_DIR ?? path.join(root, "fulfillment-output"),
    manifestUrl: environment.DWT_UPDATE_MANIFEST_URL ?? DEFAULT_MANIFEST_URL });
  if (parsed.command === "create") {
    const result = await service.create(createOptions(parsed.options));
    printCreated(result, output); return;
  }
  if (parsed.command === "show") {
    output.log(JSON.stringify(await service.show(one(parsed.positional)), null, 2)); return;
  }
  if (parsed.command === "mark-delivered") {
    output.log(JSON.stringify(await service.markDelivered(one(parsed.positional)), null, 2)); return;
  }
  if (parsed.command === "reset-device") {
    if (parsed.positional.length !== 2) throw new Error("Usage: reset-device <fulfillment/order/license reference> <deviceId>");
    output.log(JSON.stringify(await service.resetDevice(parsed.positional[0], parsed.positional[1]), null, 2)); return;
  }
  if (parsed.command === "revoke") {
    output.log(JSON.stringify(await service.revoke(one(parsed.positional), parsed.options.status === "cancelled"), null, 2)); return;
  }
  if (parsed.command === "replace-license") {
    const reference = one(parsed.positional);
    const result = await service.replace(reference, createOptions(parsed.options));
    printCreated(result, output); return;
  }
  throw new Error("Commands: create, list, show, mark-delivered, reset-device, revoke, replace-license");
}

function createOptions(options) {
  if (!options.order) throw new Error("--order is required.");
  return { orderReference: options.order, customerReference: options.customer ?? null,
    maxDevices: options["max-devices"] === undefined ? 1 : integer(options["max-devices"], "--max-devices"),
    expiresAtEpochSeconds: options["expires-at"] === undefined ? null : integer(options["expires-at"], "--expires-at"),
    notes: options.notes ?? null };
}
function printCreated(result, output) {
  output.log(`Fulfillment ID: ${result.record.fulfillmentId}`);
  output.log(`License ID: ${result.record.licenseId}`);
  output.log(`License Key: ${result.licenseKey}`);
  output.log(`Delivery artifact: ${result.deliveryPath}`);
  output.log("WARNING: The delivery file contains the customer's License Key. Deliver/store it securely and delete it when no longer needed.");
}
function one(values) { if (values.length !== 1) throw new Error("Expected exactly one fulfillment/order/license reference."); return values[0]; }
function integer(raw, name) { const value = Number(raw); if (!Number.isSafeInteger(value)) throw new Error(`${name} must be an integer.`); return value; }

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  run(process.argv.slice(2)).catch((error) => { console.error(error instanceof Error ? error.message : "Fulfillment command failed."); process.exitCode = 1; });
}
