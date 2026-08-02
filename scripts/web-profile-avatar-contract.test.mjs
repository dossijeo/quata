import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const host = read('web/src/wasmJsMain/kotlin/com/quata/web/WebProfileHost.kt');
const uploader = read('web/src/wasmJsMain/kotlin/com/quata/web/WebProfileAvatarUploader.kt');

test('Web Profile uses the Android avatar action copy, icon and real browser sources', () => {
  assert.match(host, /CompactIcon\(Icons\.Filled\.PhotoCamera, null\)/);
  assert.match(host, /Text\("Cambiar foto de perfil"\)/);
  assert.match(host, /Text\("Elegir de galería"\)/);
  assert.match(host, /Text\("Hacer foto"\)/);
  assert.match(host, /FilePickerSource\.Gallery/);
  assert.match(host, /cameraCapture\.capturePhoto/);
  assert.doesNotMatch(host, /pending de subida segura/);
  assert.doesNotMatch(host, /enabled = false/);
});

test('Web Profile avatar upload is actor-bound, square-JPEG and never persists a Blob URL', () => {
  assert.match(uploader, /avatars\/\$profileId\/\$safeToken\.jpg/);
  assert.match(uploader, /webComposerStorageUploadContract/);
  assert.match(uploader, /canvas\.width = side; canvas\.height = side/);
  assert.match(uploader, /canvas\.toBlob[\s\S]*'image\/jpeg', 0\.9/);
  assert.match(uploader, /finally \{[\s\S]*binary\.revokePrepared[\s\S]*references\.release/);
  assert.match(uploader, /require\(isBrowserAvatarBlobUrl\(normalized\)\)/);
  assert.match(uploader, /if \(isBrowserAvatarUrl\(normalized\)\) return normalized/);
});

test('Web Profile appearance copy remains byte-for-byte aligned with Android Spanish', () => {
  assert.match(host, /AppearanceSettingsStrings\("Activar Qüata TouchFlow", "Modo de color", "Sistema", "Modo Oscuro", "Modo Claro"\)/);
});
