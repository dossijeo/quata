const encoder = new TextEncoder();
const PBKDF2_PASSWORD_FORMAT = "pbkdf2_sha256";
const MIN_PBKDF2_ITERATIONS = 100_000;
const MAX_PBKDF2_ITERATIONS = 1_000_000;
// Registrations currently cap passwords at 128 characters. Keep a deliberately
// larger upper bound here so lifecycle verification remains compatible with
// historical rows while refusing attacker-controlled, unbounded KDF input.
const MAX_PASSWORD_CHARACTERS = 4_096;

export async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return bytesToHex(new Uint8Array(digest));
}

export async function hashRegistrationPassword(password, salt = randomHex(16)) {
  const iterations = 210_000;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: hexToBytes(salt),
      iterations,
    },
    key,
    256,
  );
  return `${PBKDF2_PASSWORD_FORMAT}$${iterations}$${salt}$${bytesToHex(new Uint8Array(bits))}`;
}

export async function verifyRegistrationPassword(stored, candidate) {
  const parts = String(stored || "").split("$");
  if (!isPasswordCandidate(candidate) || parts.length !== 4 || parts[0] !== PBKDF2_PASSWORD_FORMAT) return false;
  const iterations = Number(parts[1]);
  const salt = parts[2];
  const expected = parts[3];
  if (
    !Number.isInteger(iterations) ||
    iterations < MIN_PBKDF2_ITERATIONS ||
    iterations > MAX_PBKDF2_ITERATIONS ||
    !/^[0-9a-f]{32}$/i.test(salt) ||
    !/^[0-9a-f]{64}$/i.test(expected)
  ) {
    return false;
  }
  try {
    const key = await crypto.subtle.importKey(
      "raw",
      encoder.encode(candidate),
      "PBKDF2",
      false,
      ["deriveBits"],
    );
    const actualBits = await crypto.subtle.deriveBits(
      {
        name: "PBKDF2",
        hash: "SHA-256",
        salt: hexToBytes(salt),
        iterations,
      },
      key,
      256,
    );
    return constantTimeEqualHex(expected, bytesToHex(new Uint8Array(actualBits)));
  } catch {
    // Credential parsing or WebCrypto failures must never authorize an action.
    return false;
  }
}

/**
 * Verifies every credential format that can legitimately exist in
 * community_profiles. New registrations use PBKDF2; SHA-256 and pass_plain are
 * retained only as a migration bridge for existing accounts.
 */
export async function verifyProfilePassword(profile, candidate) {
  if (!isPasswordCandidate(candidate)) return false;
  const passHash = typeof profile?.pass_hash === "string" ? profile.pass_hash.trim() : "";
  // pass_hash is authoritative whenever it is present. In particular, a
  // malformed or unknown hash must not silently downgrade to pass_plain:
  // otherwise anyone who can corrupt a stored hash could still authenticate
  // with a legacy plaintext value. pass_plain is only a bridge for rows whose
  // pass_hash is genuinely absent (or whitespace).
  if (passHash) {
    if (passHash.toLowerCase().startsWith("pbkdf2")) {
      return verifyRegistrationPassword(passHash, candidate);
    }
    if (/^[0-9a-f]{64}$/i.test(passHash)) {
      return constantTimeEqualHex(passHash, await sha256Hex(candidate));
    }
    return false;
  }
  const passPlain = typeof profile?.pass_plain === "string" ? profile.pass_plain : "";
  return passPlain.length > 0 && constantTimeEqual(passPlain, candidate);
}

export async function hashRecoveryAnswer(answer, pepper) {
  const normalized = String(answer || "").normalize("NFKC").trim().toLocaleLowerCase("es");
  return `v1:${await sha256Hex(`${normalized}:${pepper}`)}`;
}

export async function recoverySecretPatch(question, answer, pepper) {
  const normalizedQuestion = typeof question === "string" ? question.trim() : "";
  const normalizedAnswer = typeof answer === "string" ? answer.trim() : "";
  if (!normalizedQuestion || !normalizedAnswer || !pepper || pepper.length < 32) {
    throw new Error("recovery_secret_invalid");
  }
  return {
    secret_question: normalizedQuestion,
    secret_answer: null,
    secret_answer_hash: await hashRecoveryAnswer(normalizedAnswer, pepper),
  };
}

export async function registrationPhoneHash(countryCode, phoneLocal, pepper) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const local = String(phoneLocal ?? "").replace(/\D/g, "");
  if (!country || !local || !pepper || pepper.length < 32) throw new Error("phone_hash_invalid");
  return sha256Hex(`+${country}${local}:${pepper}`);
}

export function randomBase64Url(byteLength) {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function randomHex(byteLength) {
  return bytesToHex(crypto.getRandomValues(new Uint8Array(byteLength)));
}

export function constantTimeEqualHex(left, right) {
  const a = String(left || "").toLowerCase();
  const b = String(right || "").toLowerCase();
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let index = 0; index < a.length; index += 1) {
    difference |= a.charCodeAt(index) ^ b.charCodeAt(index);
  }
  return difference === 0;
}

function constantTimeEqual(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

function isPasswordCandidate(candidate) {
  return typeof candidate === "string" && candidate.length > 0 && candidate.length <= MAX_PASSWORD_CHARACTERS;
}

function bytesToHex(bytes) {
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function hexToBytes(value) {
  const result = new Uint8Array(value.length / 2);
  for (let index = 0; index < result.length; index += 1) {
    result[index] = Number.parseInt(value.slice(index * 2, index * 2 + 2), 16);
  }
  return result;
}
