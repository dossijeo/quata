import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

const repoRoot = resolve(import.meta.dirname, "..");
const source = (path) => readFile(resolve(repoRoot, path), "utf8");

test("LANG-FASTTEXT-PARITY-001 Android, Web and iOS use the shared FastText detector", async () => {
  const commonDetector = await source("core/src/commonMain/kotlin/com/quata/core/language/FastTextLanguageDetector.kt");
  const commonIdentifier = await source("core/src/commonMain/kotlin/com/quata/core/language/FastTextTextLanguageIdentifier.kt");
  const androidIdentifier = await source("app/src/main/java/com/quata/core/language/QuataLanguageIdentifier.kt");
  const webIdentifier = await source("web/src/wasmJsMain/kotlin/com/quata/web/BrowserFastTextLanguageIdentifier.kt");
  const iosIdentifier = await source("core/src/iosMain/kotlin/com/quata/core/language/IosFastTextLanguageIdentifier.kt");
  const iosProject = await source("iosApp/project.yml");

  assert.match(commonDetector, /const val ModelAssetName = "lang_id_fasttext\.bin"/);
  assert.match(commonDetector, /FastTextModelReader\(bytes\)\.read\(::FastTextLanguageDetector\)/);
  assert.match(commonIdentifier, /FastTextLanguageDetector\.fromByteArray\(modelBytes\(\)\)/);

  assert.match(androidIdentifier, /assets\s*\.\s*open\(FastTextLanguageDetector\.ModelAssetName\)/);
  assert.match(webIdentifier, /FastTextTextLanguageIdentifier\(::fetchBrowserFastTextModelBytes\)/);
  assert.match(webIdentifier, /FastTextLanguageDetector\.ModelAssetName\.toJsString\(\)/);
  assert.match(iosIdentifier, /FastTextTextLanguageIdentifier\(::readIosFastTextModelBytes\)/);
  assert.match(iosIdentifier, /NSBundle\.mainBundle\.pathForResource/);

  assert.match(iosProject, /app\/src\/main\/assets\/lang_id_fasttext\.bin/);
  for (const forbidden of [
    commonDetector,
    commonIdentifier,
    androidIdentifier,
    webIdentifier,
    iosIdentifier,
  ]) {
    assert.doesNotMatch(forbidden, /LanguageIdentifier\.kt/);
    assert.doesNotMatch(forbidden, /startsWith\("el "\)|contains\(" que "\)|contains\(" the "\)/);
  }

  await access(resolve(repoRoot, "app/src/main/assets/lang_id_fasttext.bin"));
  await access(resolve(repoRoot, "web/src/wasmJsMain/resources/lang_id_fasttext.bin"));
});
