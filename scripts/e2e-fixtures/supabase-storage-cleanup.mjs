import { readFile } from "node:fs/promises";
import { Client } from "pg";

const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

async function pgConnectionConfig() {
  const raw = (await readFile(process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE, "utf8")).trim();
  const ca = await readFile(process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE, "utf8");
  const url = new URL(raw);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return { connectionString: url.toString(), ssl: { ca, rejectUnauthorized: true } };
}

export async function withReadOnlyPg(action) {
  const client = new Client(await pgConnectionConfig());
  await client.connect();
  try {
    await client.query("begin read only");
    const result = await action(client);
    await client.query("rollback");
    return result;
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end();
  }
}

export async function storageObjectCount({ bucket = "community-posts", storagePath }) {
  if (!storagePath || storagePath.includes("..")) throw new Error("storage_path_invalid");
  return withReadOnlyPg(async (client) => {
    const result = await client.query(
      "select count(*)::int as count from storage.objects where bucket_id = $1 and name = $2",
      [bucket, storagePath],
    );
    return Number(result.rows[0]?.count ?? 0);
  });
}

export async function assertStorageObjectAbsent({ bucket = "community-posts", storagePath }) {
  const physicalResidue = await storageObjectCount({ bucket, storagePath });
  if (physicalResidue !== 0) throw new Error(`storage_residue_present:${bucket}:${storagePath}`);
  return physicalResidue;
}
