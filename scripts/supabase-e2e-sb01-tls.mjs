import { readFile } from "node:fs/promises";

const CA_PEM_ENV = "SUPABASE_DB_TLS_CA_PEM";
const CA_FILE_ENV = "SUPABASE_DB_TLS_CA_FILE";
const MAX_CA_BYTES = 1024 * 1024;
const UNSAFE_SSL_PARAMETERS = ["ssl", "sslrootcert", "sslcert", "sslkey", "sslpassword"];
const ACCEPTED_SSL_MODES = new Set(["require", "verify-ca", "verify-full"]);

function failure(code) {
  // Do not include paths, PEM contents, URLs, users, or driver errors in a failure.
  return new Error(code);
}

function validateCertificateAuthority(value) {
  if (!value || Buffer.byteLength(value, "utf8") > MAX_CA_BYTES) throw failure("tls_ca_invalid");
  if (!/-----BEGIN CERTIFICATE-----[\s\S]+-----END CERTIFICATE-----/.test(value)) {
    throw failure("tls_ca_invalid");
  }
  return value;
}

export async function loadSb01CertificateAuthority(environment = process.env) {
  const pem = environment[CA_PEM_ENV]?.trim();
  const file = environment[CA_FILE_ENV]?.trim();
  if (Boolean(pem) === Boolean(file)) {
    throw failure(pem ? "tls_ca_source_ambiguous" : "tls_ca_not_configured");
  }
  if (pem) return validateCertificateAuthority(pem);
  try {
    return validateCertificateAuthority(await readFile(file, "utf8"));
  } catch (error) {
    if (error?.message?.startsWith("tls_ca_")) throw error;
    throw failure("tls_ca_file_unreadable");
  }
}

export function buildSb01TlsConnection(connectionString, certificateAuthority) {
  let url;
  try { url = new URL(connectionString); } catch { throw failure("invalid_database_url"); }
  if (!/^postgres(?:ql)?:$/i.test(url.protocol)) throw failure("invalid_database_url");

  const sslMode = url.searchParams.get("sslmode");
  if (sslMode && !ACCEPTED_SSL_MODES.has(sslMode.toLowerCase())) throw failure("unsafe_sslmode");
  for (const parameter of UNSAFE_SSL_PARAMETERS) {
    if (url.searchParams.has(parameter)) throw failure("unsafe_ssl_connection_parameter");
  }

  // node-postgres lets SSL options supplied in a connection string replace `ssl`.
  // Remove sslmode after validating it, then provide the only SSL configuration here.
  url.searchParams.delete("sslmode");
  return {
    connectionString: url.toString(),
    ssl: {
      ca: validateCertificateAuthority(certificateAuthority),
      rejectUnauthorized: true,
      minVersion: "TLSv1.2",
    },
  };
}

export const sb01TlsEnvironmentNames = Object.freeze({ CA_PEM_ENV, CA_FILE_ENV });
