import { createHash, generateKeyPairSync } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const outputDirectory = resolve(".local-keys");
mkdirSync(outputDirectory, { recursive: true });
const { privateKey, publicKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "spki", format: "pem" },
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
});
const publicDer = Buffer.from(publicKey.replace(/-----(?:BEGIN|END) PUBLIC KEY-----|\s/g, ""), "base64");
const fingerprint = createHash("sha256").update(publicDer).digest("hex");
writeFileSync(resolve(outputDirectory, "license-signing-private-key.pem"), privateKey, { mode: 0o600 });
writeFileSync(resolve(outputDirectory, "license-signing-public-key.pem"), publicKey);
writeFileSync(resolve(outputDirectory, "license-signing-public-key.sha256"), `${fingerprint}\n`);
console.log(`Generated TEST/LOCAL key material in .local-keys (SPKI SHA-256: ${fingerprint}).`);
console.log("Do not use automatically generated development keys as production keys.");
