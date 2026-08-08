import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const portable = await readFile(
  new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/richtext/QuataPortableRichTextEditor.kt", import.meta.url),
  "utf8",
);
const web = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url), "utf8");
const ios = await readFile(
  new URL("../feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt", import.meta.url),
  "utf8",
);

test("Official editor Web and iOS use the common portable rich text editor", () => {
  assert.match(portable, /fun QuataPortableRichTextEditorBox\(/);
  assert.match(portable, /QuataPortableRichTextFieldTestTag/);
  assert.match(portable, /quata-portable-rich-text-field/);
  assert.match(portable, /QuataRichTextEditorState\(initialHtml\)/);
  assert.match(portable, /state\.updateBlockText\(block\.id, value\)/);
  assert.match(portable, /state(?:::|\.)toggleBold/);
  assert.match(portable, /state(?:::|\.)toggleItalic/);
  assert.match(portable, /state(?:::|\.)toggleUnderline/);
  assert.match(portable, /state\.toggleList\("bullet"\)/);
  assert.match(portable, /state\.toggleList\("numbered"\)/);
  assert.match(portable, /state\.html/);

  assert.match(web, /QuataPortableRichTextEditorBox\(/);
  assert.match(ios, /QuataPortableRichTextEditorBox\(/);
});

test("Official editor no longer accepts browser prompt or plain iOS text field as product rich text editor", () => {
  assert.doesNotMatch(web, /webPromptForOfficialHtml|globalThis\.prompt/);
  const bodyEditor = ios.slice(ios.indexOf("bodyEditorAction ="), ios.indexOf("imagePicker ="));
  assert.doesNotMatch(bodyEditor, /OutlinedTextField/);
  assert.doesNotMatch(bodyEditor, /var editing by remember/);
});

test("Official editor rich text parity contract stays hermetic", () => {
  for (const source of [portable, web, ios]) {
    assert.doesNotMatch(source, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
  }
});
