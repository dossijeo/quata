import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('the iOS Auth host exposes focus-capable native inputs without replacing shared Compose UI', async () => {
  const [host, bridge, login, phoneField, textField] = await Promise.all([
    source('feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosAuthHost.kt'),
    source('feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosNativeAuthAccessibilityInput.kt'),
    source('feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/login/LoginForm.kt'),
    source('designsystem/src/commonMain/kotlin/com/quata/core/ui/components/PhoneInputSection.kt'),
    source('designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataTextField.kt'),
  ]);

  assert.match(host, /phoneInputAccessibilityOverlay\s*=/);
  assert.match(host, /passwordInputAccessibilityOverlay\s*=/);
  assert.match(login, /testTag\s*=\s*"auth\.phone"/);
  assert.match(login, /testTag\s*=\s*"auth\.password"/);
  assert.match(login, /testTag\s*=\s*"auth\.submit"/);
  assert.match(bridge, /UIKitView\(/);
  assert.match(bridge, /placeholder\s*=\s*label/);
  assert.match(bridge, /UITextFieldDelegateProtocol/);
  assert.match(bridge, /textFieldDidChangeSelection/);
  assert.match(bridge, /setTextColor\(UIColor\.clearColor\)/);
  assert.match(bridge, /onChanged\(textField\.text\.orEmpty\(\)\)/);
  assert.match(login, /phoneInputAccessibilityOverlay/);
  assert.match(login, /passwordInputAccessibilityOverlay/);
  assert.match(phoneField, /phoneInputOverlay/);
  assert.match(textField, /inputOverlay/);
  assert.doesNotMatch(bridge, /quata-auth-bridge|SUPABASE|password\s*=\s*"|21085800/i);
});
