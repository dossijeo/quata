import assert from "node:assert/strict";
import { access, readdir, readFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import test from "node:test";

const repoRoot = resolve(import.meta.dirname, "..");
const source = (path) => readFile(resolve(repoRoot, path), "utf8");

async function sourceFiles(dir, acc = []) {
  const entries = await readdir(resolve(repoRoot, dir), { withFileTypes: true });
  for (const entry of entries) {
    const relative = `${dir}/${entry.name}`;
    if (entry.isDirectory()) {
      if ([".gradle", ".idea", ".kotlin", "build", "node_modules"].includes(entry.name)) continue;
      await sourceFiles(relative, acc);
    } else if (/\.(kt|kts|mjs|js|swift)$/.test(entry.name)) {
      acc.push(relative);
    }
  }
  return acc;
}

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

  assert.match(androidIdentifier, /FastTextTextLanguageIdentifier\s*\{/);
  assert.match(androidIdentifier, /assets\s*\.\s*open\(FastTextLanguageDetector\.ModelAssetName\)/);
  assert.match(androidIdentifier, /identifier\(context\)\.detect\(text\)/);
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

test("LANG-FASTTEXT-PARITY-002 no basic LanguageIdentifier fallback is reintroduced", async () => {
  const files = await sourceFiles(".");
  const exactFallbackFiles = files.filter((file) => basename(file) === "LanguageIdentifier.kt");
  assert.deepEqual(exactFallbackFiles, [], "language detection must stay FastText-backed, not a basic LanguageIdentifier.kt fallback");

  const offenders = [];
  for (const file of files) {
    if (/\/(?:commonTest|androidTest|iosTest|wasmJsTest|test)\//.test(file) || file.endsWith(".test.mjs")) continue;
    const text = await source(file);
    const implementsIdentifier = /\b(?:class|object)\s+\w+\s*:\s*TextLanguageIdentifier\b/.test(text);
    const createsIdentifier = /TextLanguageIdentifier\s*\{/.test(text);
    if ((implementsIdentifier || createsIdentifier) && !/FastTextTextLanguageIdentifier|QuataLanguageIdentifier\.detect|fun interface TextLanguageIdentifier/.test(text)) {
      offenders.push(file);
    }
    if (/CommonTextLanguageIdentifier|BasicTextLanguageIdentifier|HeuristicTextLanguageIdentifier/.test(text)) {
      offenders.push(file);
    }
  }
  assert.deepEqual([...new Set(offenders)], [], "production TextLanguageIdentifier implementations must delegate to FastText");
});
