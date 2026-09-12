import {isDeepStrictEqual} from 'node:util';
import {prepareIosDeepLinkSession} from './chat-deep-link-ios-session.mjs';
import {validateOwnedNativeSessionReceipt} from './chat-deep-link-owned-session.mjs';

const snapshotKeys = ['profileId','authUserId','authSessionId','accessToken','refreshToken',
  'expiresAt','email','displayName','isOfficial'];
const uuid = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;

// Preparation only: this function performs no native install, refresh, ACK or clear.
// The caller holds the run lock. All returned snapshots and journal fields are private.
export async function prepareNativeDeepLinkExpiry(args) {
  try {
    const platform = args.platform ?? 'ios';
    if (!['ios','android'].includes(platform)) throw Error();
    const before = await args.journal.read();
    if (before.state.sessions.length !== 1) throw Error();
    const entry = before.state.sessions[0];
    if (entry.nativeSessionRenewal !== undefined || entry.iosSession !== undefined ||
        entry.androidSession !== undefined || entry.iosNativeLogin !== undefined ||
        entry.androidNativeLogin !== undefined) throw Error();
    // Reuse all existing Auth/DB/receipt/lifetime checks without weakening install.
    const verified = await prepareIosDeepLinkSession({...args, platform});
    const original = Object.fromEntries(snapshotKeys.map(key => [key, verified[key]]));
    const preparedAt = (args.now ?? (() => Math.floor(Date.now()/1000)))();
    if (!Number.isSafeInteger(preparedAt) || preparedAt < 2 || original.expiresAt <= preparedAt + 900)
      throw Error();
    const expired = {...original, expiresAt: preparedAt - 1};
    const current = await args.journal.read();
    if (!isDeepStrictEqual(current, before)) throw Error();
    const renewal = {platform, phase:'prepared', preparedAt, original, expired};
    current.state.sessions[0].nativeSessionRenewal = renewal;
    await args.journal.checkpoint(current.state);
    // A checkpoint that cannot be read back exactly never authorizes native work.
    const saved = await args.journal.read();
    if (!isDeepStrictEqual(saved, current)) throw Error();
    return structuredClone(renewal);
  } catch { throw Error('deep_link_native_expiry_preparation_unverified'); }
}

// Structural classification only. Never authorizes ACK/clear or proves a refresh
// happened: transport ordering and remote Auth identity still need verification.
export function classifyNativeDeepLinkExpirySnapshot({renewal,input,receipt}) {
  try {
    if (!renewal || !['ios','android'].includes(renewal.platform) || !['prepared','installed'].includes(renewal.phase) ||
        !Number.isSafeInteger(renewal.preparedAt) || renewal.preparedAt < 2 ||
        !isDeepStrictEqual(renewal.expired, {...renewal.original, expiresAt:renewal.preparedAt-1})) throw Error();
    // Validate original through the existing strict JWT/owner schema, not the
    // deliberately modified local expiry. The original is never overwritten.
    validateOwnedNativeSessionReceipt({input, receipt:{...receipt,privateSession:renewal.original}});
    if (renewal.original.expiresAt <= renewal.preparedAt+900) throw Error();
    const actual = receipt.privateSession;
    if (isDeepStrictEqual(actual, renewal.expired)) return 'expired_snapshot_unchanged';
    validateOwnedNativeSessionReceipt({input,receipt});
    if (['profileId','authUserId','authSessionId','email','displayName','isOfficial']
        .some(key => actual[key] !== renewal.original[key])) throw Error();
    if (isDeepStrictEqual(actual, renewal.original)) return 'original_snapshot';
    // A different token is a candidate, never evidence of one real renewal.
    if (actual.accessToken === renewal.original.accessToken ||
        actual.refreshToken === renewal.original.refreshToken || actual.expiresAt <= renewal.preparedAt+900)
      throw Error();
    return 'renewed_snapshot_unverified';
  } catch { throw Error('deep_link_native_expiry_snapshot_unverified'); }
}

// One private install intent. Native adapters must explicitly implement this new
// command; it cannot be sent as an ordinary valid-session install. Holds no retry.
export async function installNativeDeepLinkExpiry({journal,record,stepId,execute,now=()=>Math.floor(Date.now()/1000)}) {
  try {
    if (!uuid.test(stepId) || typeof execute !== 'function' ||
        ['runId','profileId','authUserId'].some(key=>!uuid.test(record[key]))) throw Error();
    const saved=await journal.read();
    if (['runId','profileId','authUserId'].some(key=>saved[key]!==record[key]) || saved.state.sessions.length!==1) throw Error();
    const entry=saved.state.sessions[0], renewal=entry.nativeSessionRenewal;
    if (['runId','profileId','authUserId'].some(key=>entry[key]!==record[key]) ||
        entry.purpose!=='deep_link' || entry.requestStarted!==true || renewal?.phase!=='prepared' ||
        renewal.install!==undefined || entry.refreshAttempt!==undefined || entry.revocation!==undefined ||
        entry.iosSession!==undefined || entry.androidSession!==undefined ||
        entry.iosNativeLogin!==undefined || entry.androidNativeLogin!==undefined ||
        entry.authSessionId!==renewal.original?.authSessionId) throw Error();
    const readInput={runId:record.runId,profileId:record.profileId,authUserId:record.authUserId,
      stage:'read-owned',stepId};
    classifyNativeDeepLinkExpirySnapshot({renewal,input:readInput,receipt:{runId:record.runId,stepId,
      stage:'read-owned',verified:true,privateSession:renewal.original}});
    const body=entry.privateLoginResponse?.body;
    if (entry.privateLoginResponse?.status!==200 || body?.session?.access_token!==renewal.original.accessToken ||
        body?.session?.refresh_token!==renewal.original.refreshToken || body?.session?.expires_at!==renewal.original.expiresAt)
      throw Error();
    const timestamp=now();
    if (!Number.isSafeInteger(timestamp) || timestamp<renewal.preparedAt ||
        renewal.expired.expiresAt>=timestamp || renewal.original.expiresAt<=timestamp+900) throw Error();
    const input={runId:record.runId,stepId,stage:'install-expired',...renewal.expired,
      originalExpiresAt:renewal.original.expiresAt};
    renewal.install={input,started:true,verified:false};
    await journal.checkpoint(saved.state);
    const durable=await journal.read();
    if (!isDeepStrictEqual(durable,saved)) throw Error();
    const receipt=await execute(structuredClone(input));
    if (!isDeepStrictEqual(receipt,{runId:record.runId,stepId,stage:'install-expired',verified:true})) throw Error();
    const completed=await journal.read();
    if (!isDeepStrictEqual(completed,saved)) throw Error();
    completed.state.sessions[0].nativeSessionRenewal.install.verified=true;
    completed.state.sessions[0].nativeSessionRenewal.phase='installed';
    await journal.checkpoint(completed.state);
    if (!isDeepStrictEqual(await journal.read(),completed)) throw Error();
    return receipt;
  } catch { throw Error('deep_link_native_expiry_install_unresolved'); }
}
