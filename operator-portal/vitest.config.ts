import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [cloudflareTest({
    wrangler: { configPath: "./wrangler.jsonc" },
    miniflare: { serviceBindings: {
      RELEASES: () => new Response("unused in unit tests", { status: 500 }),
      LICENSE_ADMIN: () => new Response("unused in unit tests", { status: 500 }),
    }, bindings: {
      TEST_MIGRATIONS: await readD1Migrations("./migrations"),
      LICENSE_ADMIN_API_URL: "https://admin.test",
      LICENSE_ADMIN_TOKEN: "rel-002-test-admin-token-not-for-production",
      UPDATE_MANIFEST_URL: "https://updates.test/manifest.json",
      CF_ACCESS_TEAM_DOMAIN: "team.cloudflareaccess.test",
      CF_ACCESS_AUD: "test-audience",
      OPERATOR_EMAIL_ALLOWLIST: "operator@example.com",
    } },
  })],
  test: { environment: "node" },
});
