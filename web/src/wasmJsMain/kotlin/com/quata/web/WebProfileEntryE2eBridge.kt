package com.quata.web

internal fun installWebProfileEntryE2eBridge(openProfile: (String) -> Unit): () -> Unit =
    installProfileEntryBridgeWhenAllowed(openProfile)

@JsFun("""
(openProfile) => {
  const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
  if (!local) return () => {};
  const bridge = Object.freeze({
    version: 1,
    openProfile: (profileId) => {
      const optedIn = new URLSearchParams(location?.search || '').get('quata-profile-entry-e2e') === '1';
      if (!optedIn) throw Error('profile_entry_bridge_not_enabled');
      if (typeof profileId !== 'string' || !profileId.trim()) throw Error('profile_entry_bridge_profile_invalid');
      openProfile(profileId.trim());
    }
  });
  globalThis.__quataProfileEntryE2eProduct = bridge;
  globalThis.document?.documentElement?.setAttribute('data-quata-profile-entry-bridge', 'ready');
  return () => {
    if (globalThis.__quataProfileEntryE2eProduct === bridge) delete globalThis.__quataProfileEntryE2eProduct;
    globalThis.document?.documentElement?.removeAttribute('data-quata-profile-entry-bridge');
  };
}
""")
private external fun installProfileEntryBridgeWhenAllowed(openProfile: (String) -> Unit): () -> Unit
