import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { createReadStream, createWriteStream } from "node:fs";
import { pipeline } from "node:stream/promises";
import { open, readFile, rename, rm, writeFile } from "node:fs/promises";

const [operation, source, destination, keyFile] = process.argv.slice(2);
if (!["encrypt", "decrypt", "encrypt-stdin"].includes(operation) || !source || !destination || !keyFile) process.exit(2);
try {
  const key = Buffer.from((await readFile(keyFile, "utf8")).trim(), "base64");
  if (key.length !== 32) throw new Error("invalid_key");
  if (operation === "encrypt") {
    const plain = await readFile(source); const nonce = randomBytes(12);
    const cipher = createCipheriv("aes-256-gcm", key, nonce); const ciphertext = Buffer.concat([cipher.update(plain), cipher.final()]);
    await writeFile(destination, Buffer.concat([Buffer.from("QLBK1"), nonce, cipher.getAuthTag(), ciphertext]), { flag: "wx" });
  } else if (operation === "encrypt-stdin") {
    const nonce = randomBytes(12); const cipher = createCipheriv("aes-256-gcm", key, nonce); const hash = createHash("sha256"); const temporary = `${destination}.cipher-${process.pid}`;
    try {
      process.stdin.on("data", (chunk) => hash.update(chunk));
      await pipeline(process.stdin, cipher, createWriteStream(temporary, { flags: "wx" }));
      await writeFile(destination, Buffer.concat([Buffer.from("QLBK1"), nonce, cipher.getAuthTag()]), { flag: "wx" });
      await pipeline(createReadStream(temporary), createWriteStream(destination, { flags: "a" }));
      process.stdout.write(hash.digest("hex"));
    } finally { await rm(temporary, { force: true }); }
  } else {
    // Do not load a production-sized custom dump into memory. Authentication is
    // checked by pipeline/final() before the restricted temporary plaintext is
    // atomically renamed into the restoration work directory.
    const header = Buffer.alloc(33); const file = await open(source, "r");
    try { if ((await file.read(header, 0, header.length, 0)).bytesRead !== header.length || header.subarray(0, 5).toString("ascii") !== "QLBK1") throw new Error("invalid_ciphertext"); } finally { await file.close(); }
    const temporary = `${destination}.part-${process.pid}`;
    try {
      const decipher = createDecipheriv("aes-256-gcm", key, header.subarray(5, 17)); decipher.setAuthTag(header.subarray(17, 33));
      await pipeline(createReadStream(source, { start: 33 }), decipher, createWriteStream(temporary, { flags: "wx" }));
      await rename(temporary, destination);
    } finally { await rm(temporary, { force: true }); }
  }
} catch { process.exit(1); }
