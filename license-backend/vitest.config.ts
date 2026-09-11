import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { generateKeyPairSync } from "node:crypto";
import { defineConfig } from "vitest/config";

const testSigningKeyPair = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        bindings: {
          TEST_MIGRATIONS: await readD1Migrations("./migrations"),
          LICENSE_KEY_PEPPER: "lic-004-unit-test-pepper-not-for-production",
          LICENSE_SIGNING_PRIVATE_KEY: testSigningKeyPair.privateKey,
          LICENSE_SIGNING_PUBLIC_KEY_V1: testSigningKeyPair.publicKey,
          LICENSE_ADMIN_TOKEN: "lic-009-test-admin-token-not-for-production",
          ANDROID_ALLOWED_SIGNING_CERT_SHA256: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          TEST_LICENSE_SIGNING_PUBLIC_KEY: testSigningKeyPair.publicKey,
        },
      },
    }),
  ],
  test: {
    environment: "node",
  },
});
