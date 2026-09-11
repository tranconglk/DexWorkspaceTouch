import { applyD1Migrations, env } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";

async function insertLicense(id: string, hash = `hash-${id}`): Promise<void> {
  await env.DB.prepare(
    "INSERT INTO licenses (id, license_key_hash, status, max_devices, created_at) VALUES (?, ?, ?, ?, ?)",
  ).bind(id, hash, "NEW", 1, 1_700_000_000).run();
}

describe("D1 schema", () => {
  beforeEach(async () => {
    await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
  });

  it("applies migration and creates all foundation tables", async () => {
    const result = await env.DB.prepare(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (?, ?, ?, ?) ORDER BY name",
    ).bind("licenses", "devices", "license_events", "license_challenges").all<{ name: string }>();

    expect(result.results.map((row) => row.name)).toEqual([
      "devices",
      "license_challenges",
      "license_events",
      "licenses",
    ]);
  });

  it("enforces unique license key hashes", async () => {
    await insertLicense("license-1", "same-key-hmac");

    await expect(insertLicense("license-2", "same-key-hmac")).rejects.toThrow();
  });

  it("enforces device foreign keys", async () => {
    await expect(
      env.DB.prepare(
        "INSERT INTO devices (id, license_id, installation_id, device_hash, public_key, key_algorithm, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
      ).bind("device-1", "missing-license", "installation-1", "hash", "public-key", "EC_P256", 10, 10).run(),
    ).rejects.toThrow();
  });

  it("prevents duplicate installation binding within one license", async () => {
    await insertLicense("license-binding");
    const statement =
      "INSERT INTO devices (id, license_id, installation_id, device_hash, public_key, key_algorithm, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    await env.DB.prepare(statement)
      .bind("device-1", "license-binding", "installation-1", "hash-1", "key-1", "EC_P256", 10, 10)
      .run();

    await expect(
      env.DB.prepare(statement)
        .bind("device-2", "license-binding", "installation-1", "hash-2", "key-2", "EC_P256", 10, 10)
        .run(),
    ).rejects.toThrow();
  });
});
