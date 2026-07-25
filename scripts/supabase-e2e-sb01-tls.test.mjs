import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { buildSb01TlsConnection, loadSb01CertificateAuthority } from "./supabase-e2e-sb01-tls.mjs";

const TEST_CA = "-----BEGIN CERTIFICATE-----\nunit-test-ca\n-----END CERTIFICATE-----";
const TEST_URL = "postgresql://postgres.example:password@pooler.example:5432/postgres?sslmode=require";

test("SB-01 builds a strict TLS configuration from an injected CA file", async () => {
  const directory = await mkdtemp(join(tmpdir(), "quata-sb01-tls-"));
  try {
    const certificatePath = join(directory, "supabase-ca.pem");
    await writeFile(certificatePath, TEST_CA, "utf8");
    const ca = await loadSb01CertificateAuthority({ SUPABASE_DB_TLS_CA_FILE: certificatePath });
    const config = buildSb01TlsConnection(TEST_URL, ca);
    assert.equal(config.ssl.ca, TEST_CA);
    assert.equal(config.ssl.rejectUnauthorized, true);
    assert.equal(config.ssl.minVersion, "TLSv1.2");
    assert.equal(new URL(config.connectionString).searchParams.has("sslmode"), false);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("SB-01 fails closed when a CA is missing or SSL verification is weakened", async () => {
  await assert.rejects(() => loadSb01CertificateAuthority({}), { message: "tls_ca_not_configured" });
  assert.throws(
    () => buildSb01TlsConnection("postgresql://pooler.example/postgres?sslmode=no-verify", TEST_CA),
    { message: "unsafe_sslmode" },
  );
  assert.throws(
    () => buildSb01TlsConnection("postgresql://pooler.example/postgres?sslrootcert=/tmp/other.pem", TEST_CA),
    { message: "unsafe_ssl_connection_parameter" },
  );
});
