import { classifyBrowserRequest } from './web-browser-network-policy.mjs';

export function shareTargetNetworkDecision(request, localOrigin) {
    const kind = classifyBrowserRequest(request, localOrigin);
    if (kind === 'turnstile-bootstrap') return 'stub-turnstile';
    if (kind === 'local') return 'continue-local';
    return 'block-unexpected';
}
