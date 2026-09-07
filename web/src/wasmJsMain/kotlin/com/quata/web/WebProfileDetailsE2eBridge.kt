@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebProfileDetailsE2eBridge(
    openDetails: () -> Unit,
    updateDetails: (displayName: String?, neighborhood: String?, countryCode: String?, phone: String?) -> Unit,
    saveProfile: () -> Unit,
    snapshotDetails: () -> String,
): () -> Unit =
    installProfileDetailsBridgeWhenAllowed(openDetails, updateDetails, saveProfile, snapshotDetails)

internal fun updateWebProfileDetailsE2eState(
    visible: Boolean,
    displayName: String?,
    neighborhood: String?,
    countryCode: String?,
    phone: String?,
) {
    updateProfileDetailsStateMarker(visible, displayName, neighborhood, countryCode, phone)
}

@JsFun(
    """(openDetails, updateDetails, saveProfile, snapshotDetails) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      if (!local) return () => {};
      const writeSnapshot = () => {
        const element = globalThis.document?.documentElement;
        if (!element) return null;
        const parts = String(snapshotDetails() || '').split('\u001F');
        if (parts[0] === 'true') element.setAttribute('data-quata-account-details-visible', 'true');
        else element.removeAttribute('data-quata-account-details-visible');
        const set = (key, value) => {
          if (typeof value === 'string') element.setAttribute(key, value);
          else element.removeAttribute(key);
        };
        set('data-quata-account-details-display-name', parts[1] || '');
        set('data-quata-account-details-neighborhood', parts[2] || '');
        set('data-quata-account-details-country-code', parts[3] || '');
        set('data-quata-account-details-phone', parts[4] || '');
        return parts;
      };
      const assertOptedIn = () => {
        const params = new URLSearchParams(location?.search || '');
        const optedIn = params.get('quata-account-details-e2e') === '1' &&
          globalThis.localStorage?.getItem('quata_account_details_e2e_opt_in') === 'I_ACCEPT_WEB_ACCOUNT_DETAILS_FIXTURE';
        if (!optedIn) throw Error('account_details_bridge_not_enabled');
      };
      const bridge = Object.freeze({
        version: 1,
        openDetails: () => {
          assertOptedIn();
          openDetails();
          globalThis.document?.documentElement?.setAttribute('data-quata-account-details-screen', 'details');
          queueMicrotask(writeSnapshot);
        },
        updateDetails: (displayName, neighborhood, countryCode, phone) => {
          assertOptedIn();
          updateDetails(
            typeof displayName === 'string' ? displayName : null,
            typeof neighborhood === 'string' ? neighborhood : null,
            typeof countryCode === 'string' ? countryCode : null,
            typeof phone === 'string' ? phone : null
          );
          globalThis.document?.documentElement?.setAttribute('data-quata-account-details-updated', 'true');
          queueMicrotask(writeSnapshot);
        },
        saveProfile: () => {
          assertOptedIn();
          saveProfile();
          globalThis.document?.documentElement?.setAttribute('data-quata-account-details-save-requested', 'true');
        },
        snapshotDetails: () => {
          assertOptedIn();
          return writeSnapshot();
        }
      });
      globalThis.__quataAccountDetailsE2EProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-account-details-bridge', 'ready');
      return () => {
        if (globalThis.__quataAccountDetailsE2EProduct === bridge) delete globalThis.__quataAccountDetailsE2EProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-bridge');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-screen');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-updated');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-save-requested');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-visible');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-display-name');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-neighborhood');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-country-code');
        globalThis.document?.documentElement?.removeAttribute('data-quata-account-details-phone');
      };
    }""",
)
private external fun installProfileDetailsBridgeWhenAllowed(
    openDetails: () -> Unit,
    updateDetails: (displayName: String?, neighborhood: String?, countryCode: String?, phone: String?) -> Unit,
    saveProfile: () -> Unit,
    snapshotDetails: () -> String,
): () -> Unit

@JsFun(
    """(visible, displayName, neighborhood, countryCode, phone) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-account-details-e2e') === '1' &&
        globalThis.localStorage?.getItem('quata_account_details_e2e_opt_in') === 'I_ACCEPT_WEB_ACCOUNT_DETAILS_FIXTURE';
      const element = globalThis.document?.documentElement;
      if (!element || !local || !optedIn) return;
      if (visible) element.setAttribute('data-quata-account-details-visible', 'true');
      else element.removeAttribute('data-quata-account-details-visible');
      const set = (key, value) => {
        if (typeof value === 'string') element.setAttribute(key, value);
        else element.removeAttribute(key);
      };
      set('data-quata-account-details-display-name', displayName);
      set('data-quata-account-details-neighborhood', neighborhood);
      set('data-quata-account-details-country-code', countryCode);
      set('data-quata-account-details-phone', phone);
    }""",
)
private external fun updateProfileDetailsStateMarker(
    visible: Boolean,
    displayName: String?,
    neighborhood: String?,
    countryCode: String?,
    phone: String?,
)
