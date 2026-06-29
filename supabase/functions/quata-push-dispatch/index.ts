import { createClient } from "npm:@supabase/supabase-js@2";

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

type PushRequest = {
  message_id?: number | string;
};

type ChatMessage = {
  id: number;
  thread_id: number;
  sender_profile_id: string;
  body: string | null;
  deleted_at: string | null;
};

type ChatThread = {
  id: number;
  type: string;
  subject: string | null;
  title: string | null;
};

type CommunityProfile = {
  id: string;
  display_name: string | null;
  nombre: string | null;
};

type PushToken = {
  id: string;
  user_id: string;
  token: string;
};

type FcmErrorBody = {
  error?: {
    status?: string;
    message?: string;
    details?: Array<{
      errorCode?: string;
    }>;
  };
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  try {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);
    const payload = await parsePushRequest(req);
    const messageId = Number(payload.message_id);
    if (!Number.isFinite(messageId) || messageId <= 0) {
      return jsonResponse({ error: "invalid_message_id" }, 400);
    }
    const result = await dispatchChatPush(messageId);
    return jsonResponse(result);
  } catch (error) {
    console.error(error);
    return jsonResponse(
      { error: "internal_error", detail: error instanceof Error ? error.message : "Unexpected error" },
      500,
    );
  }
});

async function parsePushRequest(req: Request): Promise<PushRequest> {
  const raw = await req.text();
  if (!raw.trim()) return {};
  try {
    return JSON.parse(raw) as PushRequest;
  } catch (error) {
    const malformedPgNetBody = raw.match(/message_id\s*[:=]\s*"?(\d+)/i);
    if (malformedPgNetBody?.[1]) return { message_id: malformedPgNetBody[1] };
    throw error;
  }
}

async function dispatchChatPush(messageId: number) {
  const supabaseUrl = requireEnv("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || envDictionaryValue("SUPABASE_SECRET_KEYS");
  if (!serviceRoleKey) throw new Error("Missing service role key");

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "X-Client-Info": "quata-push-dispatch" } },
  });

  const { data: message, error: messageError } = await admin
    .from("chat_messages")
    .select("id,thread_id,sender_profile_id,body,deleted_at")
    .eq("id", messageId)
    .maybeSingle();
  if (messageError) throw messageError;
  if (!message || (message as ChatMessage).deleted_at) return { result: true, skipped: "message_not_found" };
  const chatMessage = message as ChatMessage;

  const [{ data: thread, error: threadError }, { data: sender, error: senderError }] = await Promise.all([
    admin.from("chat_threads").select("id,type,subject,title").eq("id", chatMessage.thread_id).maybeSingle(),
    admin.from("community_profiles").select("id,display_name,nombre").eq("id", chatMessage.sender_profile_id).maybeSingle(),
  ]);
  if (threadError) throw threadError;
  if (senderError) throw senderError;
  if (!thread || !sender) return { result: true, skipped: "missing_thread_or_sender" };

  const { data: participants, error: participantsError } = await admin
    .from("chat_participants")
    .select("profile_id")
    .eq("thread_id", chatMessage.thread_id)
    .is("left_at", null)
    .eq("is_deleted", false)
    .is("muted_at", null)
    .neq("profile_id", chatMessage.sender_profile_id);
  if (participantsError) throw participantsError;

  const recipientIds = ((participants ?? []) as Array<{ profile_id: string }>)
    .map((participant) => participant.profile_id)
    .filter(Boolean);
  if (recipientIds.length === 0) return { result: true, recipients: 0, sent: 0 };

  const { data: tokens, error: tokensError } = await admin
    .from("push_tokens")
    .select("id,user_id,token")
    .in("user_id", recipientIds)
    .is("disabled_at", null);
  if (tokensError) throw tokensError;

  const pushTokens = ((tokens ?? []) as PushToken[]).filter((row) => row.token);
  if (pushTokens.length === 0) return { result: true, recipients: recipientIds.length, sent: 0 };

  const serviceAccount = firebaseServiceAccount();
  const accessToken = await firebaseAccessToken(serviceAccount);
  const title = notificationTitle(thread as ChatThread, sender as CommunityProfile);
  const body = notificationBody(chatMessage);
  let sent = 0;
  let skipped = 0;

  for (const pushToken of pushTokens) {
    const inserted = await reserveDelivery(admin, chatMessage.id, pushToken.user_id, pushToken.id);
    if (!inserted) {
      skipped += 1;
      continue;
    }
    const response = await sendFcmMessage(serviceAccount.project_id, accessToken, pushToken.token, {
      title,
      body,
      threadId: String(chatMessage.thread_id),
      conversationId: `sb:${chatMessage.thread_id}`,
      messageId: String(chatMessage.id),
    });
    if (response.ok) {
      sent += 1;
      await markDelivery(admin, chatMessage.id, pushToken.user_id, pushToken.id, "sent", null);
    } else {
      const errorText = await response.text();
      await markDelivery(admin, chatMessage.id, pushToken.user_id, pushToken.id, "error", errorText.slice(0, 800));
      if (isPermanentFcmTokenError(errorText)) {
        await disablePushToken(admin, pushToken.id, errorText.slice(0, 800));
      }
    }
  }

  return { result: true, recipients: recipientIds.length, tokens: pushTokens.length, sent, skipped };
}

async function disablePushToken(
  admin: ReturnType<typeof createClient>,
  tokenId: string,
  errorText: string,
) {
  const { error } = await admin
    .from("push_tokens")
    .update({ disabled_at: new Date().toISOString(), last_error_text: errorText })
    .eq("id", tokenId);
  if (error) throw error;
}

function isPermanentFcmTokenError(errorText: string): boolean {
  let parsed: FcmErrorBody | null = null;
  try {
    parsed = JSON.parse(errorText) as FcmErrorBody;
  } catch {
    return false;
  }
  const fcmErrorCode = parsed?.error?.details
    ?.map((detail) => detail.errorCode)
    .find((code): code is string => Boolean(code));
  return fcmErrorCode === "UNREGISTERED" ||
    fcmErrorCode === "INVALID_ARGUMENT" ||
    parsed?.error?.status === "NOT_FOUND";
}

async function reserveDelivery(
  admin: ReturnType<typeof createClient>,
  messageId: number,
  profileId: string,
  tokenId: string,
): Promise<boolean> {
  const { error } = await admin.from("push_delivery_log").insert({
    message_id: messageId,
    profile_id: profileId,
    push_token_id: tokenId,
    status: "reserved",
  });
  if (!error) return true;
  if (error.code === "23505") return false;
  throw error;
}

async function markDelivery(
  admin: ReturnType<typeof createClient>,
  messageId: number,
  profileId: string,
  tokenId: string,
  status: string,
  errorText: string | null,
) {
  const { error } = await admin
    .from("push_delivery_log")
    .update({ status, error_text: errorText, sent_at: status === "sent" ? new Date().toISOString() : null })
    .eq("message_id", messageId)
    .eq("profile_id", profileId)
    .eq("push_token_id", tokenId);
  if (error) throw error;
}

async function sendFcmMessage(
  projectId: string,
  accessToken: string,
  token: string,
  payload: { title: string; body: string; threadId: string; conversationId: string; messageId: string },
) {
  return fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        android: { priority: "HIGH" },
        data: {
          type: "chat_message",
          title: payload.title,
          body: payload.body,
          thread_id: payload.threadId,
          conversation_id: payload.conversationId,
          message_id: payload.messageId,
        },
      },
    }),
  });
}

function notificationTitle(thread: ChatThread, sender: CommunityProfile): string {
  if (thread.type === "sos") return "SOS";
  return thread.title?.trim() || thread.subject?.trim() || displayName(sender) || "Chat";
}

function notificationBody(message: ChatMessage): string {
  return message.body?.trim() || "Nuevo adjunto";
}

function displayName(profile: CommunityProfile): string {
  return profile.display_name?.trim() || profile.nombre?.trim() || "Qüata";
}

function firebaseServiceAccount(): ServiceAccount {
  const encoded = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON_B64");
  const raw = encoded?.trim()
    ? new TextDecoder().decode(Uint8Array.from(atob(encoded.trim()), (char) => char.charCodeAt(0)))
    : requireEnv("FIREBASE_SERVICE_ACCOUNT_JSON");
  const parsed = JSON.parse(raw) as ServiceAccount;
  if (!parsed.project_id || !parsed.client_email || !parsed.private_key) {
    throw new Error("Invalid Firebase service account");
  }
  return parsed;
}

async function firebaseAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(
    {
      alg: "RS256",
      typ: "JWT",
    },
    {
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    serviceAccount.private_key,
  );
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Firebase token failed: ${await response.text()}`);
  const body = await response.json() as { access_token?: string };
  if (!body.access_token) throw new Error("Firebase token response missing access_token");
  return body.access_token;
}

async function signJwt(header: Record<string, unknown>, payload: Record<string, unknown>, privateKeyPem: string): Promise<string> {
  const encodedHeader = base64UrlJson(header);
  const encodedPayload = base64UrlJson(payload);
  const unsigned = `${encodedHeader}.${encodedPayload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${base64UrlBytes(new Uint8Array(signature))}`;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem
    .replace(/\\n/g, "\n")
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
}

function base64UrlJson(value: Record<string, unknown>): string {
  return base64UrlBytes(new TextEncoder().encode(JSON.stringify(value)));
}

function base64UrlBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function envDictionaryValue(name: string, preferredKey = "default"): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const preferred = parsed[preferredKey];
    if (typeof preferred === "string" && preferred.trim()) return preferred;
    for (const value of Object.values(parsed)) {
      if (typeof value === "string" && value.trim()) return value;
    }
  } catch {
    return null;
  }
  return null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
