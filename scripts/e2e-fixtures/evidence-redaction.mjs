import { createHash } from "node:crypto";

const evidenceSecrets = new Map();

export function evidenceSha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

export function registerEvidenceSecret(value, label = "secret") {
  const text = String(value ?? "");
  if (!text) return;
  evidenceSecrets.set(text, label);
}

export function redactEvidenceUrl(value) {
  try {
    const url = new URL(value);
    url.search = "";
    const publicPath = storagePathFromPublicUrl(url.toString());
    const objectPath = storagePathFromUploadUrl(url.toString());
    const storagePath = publicPath ?? objectPath;
    if (storagePath) {
      const marker = publicPath
        ? "/storage/v1/object/public/chat-attachments/"
        : "/storage/v1/object/chat-attachments/";
      const index = url.pathname.indexOf(marker);
      if (index >= 0) {
        url.pathname = `${url.pathname.slice(0, index + marker.length)}<storage-path-sha256:${evidenceSha256(storagePath)}>`;
      }
    }
    return url.toString();
  } catch {
    return redactEvidenceString(value);
  }
}

export function redactEvidenceString(value) {
  return redactRegisteredSecrets(redactBareStoragePaths(redactStoragePathsInText(String(value))))
    .replace(/\bqadata-[A-Za-z0-9_.-]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi, "<evidence-marker-redacted>")
    .replace(/(?<![A-Za-z])[A-Z]:[\\/][^"'\r\n),}]+?\.[A-Za-z0-9]+/gi, "<local-path-redacted>")
    .replace(/(?<![A-Za-z])[A-Z]:[\\/][^"'\s),}]+/gi, "<local-path-redacted>")
    .replace(/\/(?:Users|home)\/[^"'\r\n),}]+?\.[A-Za-z0-9]+/g, "<local-path-redacted>")
    .replace(/\/(?:Users|home)\/[^"'\s),}]+/g, "<local-path-redacted>")
    .replace(/\/(?:private\/)?tmp\/[^"'\r\n),}]+?\.[A-Za-z0-9]+/g, "<local-path-redacted>")
    .replace(/\/(?:private\/)?tmp\/[^"'\s),}]+/g, "<local-path-redacted>")
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, "Bearer <redacted>")
    .replace(/apikey[:=]\s*[A-Za-z0-9._-]+/gi, "apikey=<redacted>")
    .replace(/access_token["':=\s]+[A-Za-z0-9._-]+/gi, "access_token=<redacted>")
    .replace(/refresh_token["':=\s]+[A-Za-z0-9._-]+/gi, "refresh_token=<redacted>")
    .replace(/web_session_token["':=\s]+[A-Za-z0-9._-]+/gi, "web_session_token=<redacted>")
    .replace(/password["':=\s]+[^"'\s,&}]+/gi, "password=<redacted>")
    .replace(/cookie["':=\s]+[^"'\n\r}]+/gi, "cookie=<redacted>");
}

export function redactEvidenceReport(value) {
  if (typeof value === "string") return redactEvidenceString(value);
  if (Array.isArray(value)) return value.map(redactEvidenceReport);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    const normalizedKey = key.toLowerCase();
    if (typeof item === "string" && normalizedKey.includes("storagepath")) {
      return [key, `<storage-path-sha256:${evidenceSha256(item.replace(/^\/+/, ""))}>`];
    }
    if (key === "path" && typeof item === "string" && !item.includes("build-reports")) {
      return [key, `<path-sha256:${evidenceSha256(item)}>`];
    }
    return [key, redactEvidenceReport(item)];
  }));
}

function redactStoragePathsInText(value) {
  return value.replace(
    /(\/storage\/v1\/object\/(?:public\/)?chat-attachments\/)([^"'\s),]+)(\?[^"'\s)]*)?/gi,
    (_, prefix, path) => `${prefix}<storage-path-sha256:${evidenceSha256(decodeURIComponent(path).replace(/^\/+/, ""))}>`,
  );
}

function redactBareStoragePaths(value) {
  return value.replace(
    /\b(storagePath|storage_path)\s*[:=]\s*["']?([^"'\s),}]+\/[^"'\s),}]+)["']?/gi,
    (_, key, path) => `${key}=<storage-path-sha256:${evidenceSha256(path.replace(/^\/+/, ""))}>`,
  );
}

function redactRegisteredSecrets(value) {
  let redacted = value;
  for (const [secret, label] of evidenceSecrets.entries()) {
    redacted = redacted.split(secret).join(`<${label}-sha256:${evidenceSha256(secret)}>`);
  }
  return redacted;
}

function storagePathFromPublicUrl(value) {
  try {
    const url = new URL(value);
    const marker = "/storage/v1/object/public/chat-attachments/";
    const index = url.pathname.indexOf(marker);
    if (index < 0) return null;
    return decodeURIComponent(url.pathname.slice(index + marker.length)).replace(/^\/+/, "");
  } catch {
    return null;
  }
}

function storagePathFromUploadUrl(value) {
  try {
    const url = new URL(value);
    const marker = "/storage/v1/object/chat-attachments/";
    const index = url.pathname.indexOf(marker);
    if (index < 0) return null;
    return decodeURIComponent(url.pathname.slice(index + marker.length)).replace(/^\/+/, "");
  } catch {
    return null;
  }
}
