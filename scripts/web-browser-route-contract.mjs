/** Exhaustive, ordered product-route contract shared by smoke and metrics validation. */
export const SMOKE_ROUTE_CONTRACTS = Object.freeze([
    { fragment: 'auth', kind: 'auth', route: 'auth' },
    { fragment: 'feed', kind: 'public', route: 'feed' },
    { fragment: 'official', kind: 'public', route: 'official' },
    { fragment: 'chat', kind: 'private', returnRoute: 'chat' },
    { fragment: 'settings', kind: 'private', returnRoute: 'settings' },
    { fragment: 'share-target', kind: 'private', returnRoute: 'share-target' },
    { fragment: 'share-target-error', kind: 'private', returnRoute: 'share-target-error' },
    { fragment: 'notifications', kind: 'private', returnRoute: 'notifications' },
    { fragment: 'profile', kind: 'private', returnRoute: 'profile' },
    { fragment: 'composer', kind: 'private', returnRoute: 'composer' },
    { fragment: 'communities', kind: 'private', returnRoute: 'communities' },
    { fragment: 'whats-new', kind: 'private', returnRoute: 'whats-new' },
    { fragment: 'about', kind: 'private', returnRoute: 'about' },
]);

export const SMOKE_ROUTE_FRAGMENTS = Object.freeze(SMOKE_ROUTE_CONTRACTS.map(({ fragment }) => fragment));
