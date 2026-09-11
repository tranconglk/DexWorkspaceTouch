import { createPrivateKey, createPublicKey, sign, verify } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const privatePem = readFileSync(resolve(".local-keys/license-signing-private-key.pem"), "utf8");
const publicPem = readFileSync(resolve(".local-keys/license-signing-public-key.pem"), "utf8");
const privateKey = createPrivateKey({ key: privatePem, format: "pem", type: "pkcs8" });
const publicKey = createPublicKey({ key: publicPem, format: "pem", type: "spki" });
if (privateKey.asymmetricKeyType !== "rsa" || publicKey.asymmetricKeyType !== "rsa") throw new Error("Generated keys are not RSA.");
if ((publicKey.asymmetricKeyDetails?.modulusLength ?? 0) < 2048) throw new Error("Generated RSA modulus is too small.");
const payload = Buffer.from("dexworkspacetouch-license-key-generation-proof", "utf8");
const signature = sign("RSA-SHA256", payload, privateKey);
if (!verify("RSA-SHA256", payload, publicKey, signature)) throw new Error("Generated public key cannot verify the signature.");
console.log(`Generated PKCS#8/SPKI RSA key pair verified (${publicKey.asymmetricKeyDetails.modulusLength} bits).`);
