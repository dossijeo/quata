import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');

test('iOS test bundles generate plists and retain explicit simulator-safe identifiers', async () => {
  const project = await readFile(resolve(root, 'iosApp/project.yml'), 'utf8');
  const targets = {
    QuataIosUITests: 'com.quata.ios.uitests',
    QuataIosTests: 'com.quata.ios.tests',
    QuataShareExtensionTests: 'com.quata.ios.shareextension.tests',
  };
  for (const [target, identifier] of Object.entries(targets)) {
    const section = project.match(new RegExp(`  ${target}:\\n([\\s\\S]*?)(?=^  [A-Za-z]|^schemes:)`, 'm'))?.[1] ?? '';
    assert.match(section, new RegExp(`PRODUCT_BUNDLE_IDENTIFIER: ${identifier.replaceAll('.', '\\.')}`));
    assert.match(section, /GENERATE_INFOPLIST_FILE: "YES"/);
  }
});
