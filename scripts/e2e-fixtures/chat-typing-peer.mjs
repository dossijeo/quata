const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function requireText(value, name) {
  const text = String(value ?? "").trim();
  if (!text) throw new Error(`chat_typing_peer_invalid_${name}`);
  return text;
}

/**
 * Starts an authenticated, ephemeral Supabase Realtime peer in an isolated browser context.
 * Credentials remain inside that context and are never returned in diagnostics or evidence.
 */
export async function createChatTypingPeer({
  browser,
  baseUrl,
  publishableKey,
  accessToken,
  profileId,
  conversationId,
  timeoutMs = 15_000,
}) {
  if (!browser || typeof browser.newContext !== "function") throw new Error("chat_typing_peer_invalid_browser");
  const endpoint = requireText(baseUrl, "base_url").replace(/\/+$/, "");
  const apiKey = requireText(publishableKey, "publishable_key");
  const token = requireText(accessToken, "access_token");
  const actor = requireText(profileId, "profile_id");
  const conversation = requireText(conversationId, "conversation_id");
  if (!uuid.test(actor) || !/^sb:\d+$/.test(conversation)) throw new Error("chat_typing_peer_invalid_identity");
  if (!Number.isFinite(timeoutMs) || timeoutMs < 1_000 || timeoutMs > 60_000) {
    throw new Error("chat_typing_peer_invalid_timeout");
  }

  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto("about:blank");
    await page.evaluate(({ endpoint, apiKey, token, actor, conversation }) => {
      const state = {
        joined: false,
        closed: false,
        failure: null,
        ref: 0,
        joinRef: null,
        sent: { true: 0, false: 0 },
        received: [],
      };
      const topic = `realtime:quata-typing-${conversation}`;
      const socketUrl = `${endpoint.replace(/^https:/, "wss:").replace(/^http:/, "ws:")}/realtime/v1/websocket?apikey=${encodeURIComponent(apiKey)}&vsn=2.0.0`;
      const socket = new WebSocket(socketUrl);
      let heartbeatTimer = null;
      const nextRef = () => String(++state.ref);
      const send = (joinRef, ref, event, payload) => socket.send(JSON.stringify([joinRef, ref, topic, event, payload]));
      socket.onopen = () => {
        const joinRef = nextRef();
        state.joinRef = joinRef;
        send(null, joinRef, "phx_join", {
          access_token: token,
          config: {
            broadcast: { ack: false, self: false },
            presence: { enabled: false },
            postgres_changes: [],
            private: false,
          },
        });
        heartbeatTimer = setInterval(() => {
          if (socket.readyState !== WebSocket.OPEN) return;
          const heartbeatRef = nextRef();
          socket.send(JSON.stringify([null, heartbeatRef, "phoenix", "heartbeat", {}]));
        }, 25_000);
      };
      socket.onmessage = (message) => {
        let frame;
        try { frame = JSON.parse(String(message.data ?? "")); } catch { return; }
        if (!Array.isArray(frame) || frame.length < 5 || frame[2] !== topic) return;
        const [, ref, , event, payload] = frame;
        if (event === "phx_reply" && ref === state.joinRef) {
          if (payload?.status === "ok") state.joined = true;
          else state.failure = "join_failed";
          return;
        }
        if (event !== "broadcast" || payload?.event !== "typing") return;
        const typing = payload?.payload;
        if (typeof typing?.profile_id !== "string" || typeof typing?.is_typing !== "boolean") return;
        state.received.push({ profileId: typing.profile_id, isTyping: typing.is_typing });
        if (state.received.length > 20) state.received.shift();
      };
      socket.onerror = () => { if (!state.closed) state.failure = "socket_error"; };
      socket.onclose = () => {
        if (heartbeatTimer !== null) clearInterval(heartbeatTimer);
        state.joined = false;
        state.closed = true;
      };
      globalThis.__quataTypingPeer = {
        state,
        sendTyping(isTyping) {
          if (!state.joined || socket.readyState !== WebSocket.OPEN) throw new Error("peer_not_joined");
          const ref = nextRef();
          send(state.joinRef, ref, "broadcast", {
            type: "broadcast",
            event: "typing",
            payload: { profile_id: actor, is_typing: Boolean(isTyping) },
          });
          state.sent[String(Boolean(isTyping))] += 1;
        },
        close() {
          if (heartbeatTimer !== null) clearInterval(heartbeatTimer);
          state.closed = true;
          try { socket.close(1000, "evidence-complete"); } catch {}
        },
      };
    }, { endpoint, apiKey, token, actor, conversation });

    await page.waitForFunction(() => {
      const state = globalThis.__quataTypingPeer?.state;
      return state?.joined === true || typeof state?.failure === "string" || state?.closed === true;
    }, null, { timeout: timeoutMs });
    const ready = await page.evaluate(() => ({
      joined: globalThis.__quataTypingPeer?.state?.joined === true,
      failure: globalThis.__quataTypingPeer?.state?.failure ?? null,
    }));
    if (!ready.joined) throw new Error(`chat_typing_peer_join_failed:${ready.failure ?? "closed"}`);

    return {
      async sendTyping(isTyping) {
        await page.evaluate((value) => globalThis.__quataTypingPeer.sendTyping(value), Boolean(isTyping));
      },
      async waitForTyping({ expectedProfileId, isTyping, after = 0, timeout = timeoutMs }) {
        const expected = requireText(expectedProfileId, "expected_profile_id");
        if (!uuid.test(expected)) throw new Error("chat_typing_peer_invalid_expected_profile_id");
        await page.waitForFunction(({ expected, isTyping, after }) => {
          const received = globalThis.__quataTypingPeer?.state?.received ?? [];
          return received.slice(after).some((entry) => entry.profileId === expected && entry.isTyping === isTyping);
        }, { expected, isTyping: Boolean(isTyping), after }, { timeout });
        return page.evaluate(() => globalThis.__quataTypingPeer.state.received.length);
      },
      async snapshot() {
        return page.evaluate(() => {
          const state = globalThis.__quataTypingPeer.state;
          return {
            joined: state.joined === true,
            sentTyping: state.sent.true,
            sentStopped: state.sent.false,
            receivedTyping: state.received.filter((entry) => entry.isTyping).length,
            receivedStopped: state.received.filter((entry) => !entry.isTyping).length,
          };
        });
      },
      async close() {
        await page.evaluate(() => globalThis.__quataTypingPeer?.close());
        await context.close();
      },
    };
  } catch (error) {
    await context.close().catch(() => {});
    throw error;
  }
}
