const TLS_QUERY_KEYS = ["ssl", "sslmode", "sslrootcert", "sslcert", "sslkey", "uselibpqcompat"];

export function pinnedTlsClientConfig(connectionString, ca) {
  const connection = new URL(connectionString);
  for (const key of TLS_QUERY_KEYS) connection.searchParams.delete(key);
  return {
    connectionString: connection.toString(),
    ssl: { ca, rejectUnauthorized: true },
  };
}
