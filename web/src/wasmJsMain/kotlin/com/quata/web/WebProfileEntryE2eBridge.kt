package com.quata.web

internal fun installWebProfileEntryE2eBridge(
    openProfile: (String) -> Unit,
    closeProfile: () -> Unit,
    openCommunityMembers: (String) -> Unit,
): () -> Unit =
    installProfileEntryBridgeWhenAllowed(openProfile, closeProfile, openCommunityMembers)

@JsFun("""
(openProfile, closeProfile, openCommunityMembers) => {
  const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
  if (!local) return () => {};
  const assertOptedIn = () => {
    const optedIn = new URLSearchParams(location?.search || '').get('quata-profile-entry-e2e') === '1';
    if (!optedIn) throw Error('profile_entry_bridge_not_enabled');
  };
  const bridge = Object.freeze({
    version: 1,
    openProfile: (profileId) => {
      assertOptedIn();
      if (typeof profileId !== 'string' || !profileId.trim()) throw Error('profile_entry_bridge_profile_invalid');
      openProfile(profileId.trim());
    },
    closeProfile: () => {
      assertOptedIn();
      closeProfile();
    },
    openCommunityMembers: (neighborhood) => {
      assertOptedIn();
      if (typeof neighborhood !== 'string' || !neighborhood.trim()) throw Error('profile_entry_bridge_neighborhood_invalid');
      openCommunityMembers(neighborhood.trim());
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
private external fun installProfileEntryBridgeWhenAllowed(
    openProfile: (String) -> Unit,
    closeProfile: () -> Unit,
    openCommunityMembers: (String) -> Unit,
): () -> Unit
