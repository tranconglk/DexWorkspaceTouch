import type { D1Migration } from "@cloudflare/vitest-plugin";

declare global {
  namespace Cloudflare {
    interface Env {
      DB: D1Database;
      TEST_MIGRATIONS: D1Migration[];
      LICENSE_KEY_PEPPER: string;
      LICENSE_SIGNING_PRIVATE_KEY: string;
      LICENSE_SIGNING_PUBLIC_KEY_V1: string;
      LICENSE_ADMIN_TOKEN: string;
      TEST_LICENSE_SIGNING_PUBLIC_KEY: string;
      LICENSE_SIGNING_KEY_ID: string;
      APP_PACKAGE_NAME: string;
      ANDROID_ALLOWED_SIGNING_CERT_SHA256: string;
    }
  }
}

export {};
