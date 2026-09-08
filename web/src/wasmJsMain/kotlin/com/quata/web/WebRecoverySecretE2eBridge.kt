@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebRecoverySecretE2eBridge(
    open: () -> Unit,
    configure: (String, String) -> Unit,
    save: () -> Unit,
    snapshot: () -> String,
): () -> Unit = installRecoverySecretBridgeWhenAllowed(open, configure, save, snapshot)

@JsFun(
    """(open, configure, save, snapshot) => {
      const allowed = () =>
        (location?.hostname === 'localhost' || location?.hostname === '127.0.0.1') &&
        new URLSearchParams(location?.search || '').get('quata-recovery-secret-e2e') === '1' &&
        globalThis.localStorage?.getItem('quata_recovery_secret_e2e_opt_in') === 'I_ACCEPT_ACCOUNT_RECOVERY_SECRET_FIXTURE';
      if (!allowed()) return () => {};
      const guard = () => { if (!allowed()) throw Error('recovery_secret_bridge_not_enabled'); };
      const bridge = Object.freeze({
        version: 1,
        open: () => { guard(); open(); },
        configure: (question, answer) => {
          guard();
          if (typeof question !== 'string' || typeof answer !== 'string') throw Error('recovery_secret_input_invalid');
          configure(question, answer);
        },
        save: () => { guard(); save(); },
        snapshot: () => {
          guard();
          const parts = String(snapshot()).split('\u001F');
          if (parts.length !== 6 || [0,2,3,4,5].some(i => !['true','false'].includes(parts[i]))) throw Error('recovery_secret_state_invalid');
          return {visible: parts[0] === 'true', question: parts[1], answerEmpty: parts[2] === 'true',
            saving: parts[3] === 'true', failed: parts[4] === 'true', saved: parts[5] === 'true'};
        }
      });
      globalThis.__quataRecoverySecretE2eProduct = bridge;
      return () => { if (globalThis.__quataRecoverySecretE2eProduct === bridge) delete globalThis.__quataRecoverySecretE2eProduct; };
    }""",
)
private external fun installRecoverySecretBridgeWhenAllowed(
    open: () -> Unit,
    configure: (String, String) -> Unit,
    save: () -> Unit,
    snapshot: () -> String,
): () -> Unit
