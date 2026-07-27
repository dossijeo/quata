/*
 * Stable, non-interactive observation boundary for Compose/Wasm browser tests.
 *
 * The product UI remains the Compose canvas. Do not add click handlers or editable inputs here:
 * those would test a DOM surrogate rather than the application.
 */
(() => {
  const contractVersion = '1';
  const host = document.querySelector('quata-test-contract');
  if (!host) return;

  const root = host.attachShadow({ mode: 'open' });
  root.innerHTML = `
    <style>:host { display: contents; } [data-testid] { display: none; }</style>
    <section data-testid="web-test-contract" data-contract-version="${contractVersion}" aria-hidden="true">
      <output data-testid="web-surface" data-surface="boot"></output>
      <output data-testid="web-route" data-route="boot"></output>
      <output data-testid="auth-phone-input" data-control="auth.phone"></output>
      <output data-testid="auth-password-input" data-control="auth.password"></output>
      <output data-testid="auth-submit" data-control="auth.submit"></output>
      <output data-testid="auth-forgot-password" data-control="auth.forgot-password"></output>
      <output data-testid="auth-register" data-control="auth.register"></output>
      <output data-testid="chat-refresh" data-control="chat.refresh"></output>
      <output data-testid="chat-new-conversation" data-control="chat.new-conversation"></output>
      <output data-testid="chat-message-input" data-control="chat.message"></output>
      <output data-testid="chat-send" data-control="chat.send"></output>
      <output data-testid="chat-back" data-control="chat.back"></output>
    </section>`;

  const contractRoot = root.querySelector('[data-testid="web-test-contract"]');
  const surface = root.querySelector('[data-testid="web-surface"]');
  const route = root.querySelector('[data-testid="web-route"]');
  globalThis.__quataWebTestContract = {
    version: contractVersion,
    setState(nextSurface, nextRoute) {
      contractRoot.dataset.surface = nextSurface;
      contractRoot.dataset.route = nextRoute;
      surface.dataset.surface = nextSurface;
      route.dataset.route = nextRoute;
    },
  };
})();
