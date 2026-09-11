import type { D1Migration } from "@cloudflare/vitest-plugin";
declare global {
  namespace Cloudflare {
    interface Env {
    FULFILLMENT_DB: D1Database;
      TEST_MIGRATIONS: D1Migration[];
    LICENSE_ADMIN_API_URL: string;
    LICENSE_ADMIN_TOKEN: string;
    UPDATE_MANIFEST_URL: string;
    CF_ACCESS_TEAM_DOMAIN: string;
    CF_ACCESS_AUD: string;
    OPERATOR_EMAIL_ALLOWLIST: string;
    }
  }
}
export {};
