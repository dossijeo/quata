import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const contract = await readFile('web/src/wasmJsMain/resources/web-test-contract.js', 'utf8');
const index = await readFile('web/src/wasmJsMain/resources/index.html', 'utf8');

test('WEB-TEST-001 publishes an inert, versioned shadow DOM observation contract', () => {
  assert.match(index, /<quata-test-contract aria-hidden="true"><\/quata-test-contract>/);
  assert.match(index, /<script src="web-test-contract\.js"><\/script>/);
  assert.match(contract, /attachShadow\(\{ mode: 'open' \}\)/);
  assert.match(contract, /data-contract-version="\$\{contractVersion\}"/);
  assert.match(contract, /aria-hidden="true"/);
  assert.doesNotMatch(contract, /addEventListener\s*\(\s*['"]click/);
});

test('WEB-TEST-001 keeps Auth and Chat v1 selectors stable', () => {
  for (const selector of [
    'auth-phone-input', 'auth-password-input', 'auth-submit', 'auth-forgot-password', 'auth-register',
    'chat-refresh', 'chat-new-conversation', 'chat-message-input', 'chat-send', 'chat-back',
  ]) assert.match(contract, new RegExp(`data-testid=\\"${selector}\\"`));
});
