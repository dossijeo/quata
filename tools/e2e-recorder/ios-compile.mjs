#!/usr/bin/env node
import { compileMacro, readMacro } from "./lib/macro-core.mjs";

const file = process.argv[2];
if (!file) {
  console.error("Usage: node tools/e2e-recorder/ios-compile.mjs <macro.json>");
  process.exit(64);
}

const macro = await readMacro(file);
const compiled = compileMacro(macro);
if (!compiled.runnable) {
  console.error(JSON.stringify(compiled.diagnostics, null, 2));
  process.exit(2);
}

console.log(renderSwift(compiled));

function renderSwift(compiled) {
  const lines = [
    `// Generated from ${compiled.flow}. Review before committing to XCTest.`,
    "let app = XCUIApplication()",
  ];
  for (const step of compiled.steps) {
    const selector = step.replay;
    if (step.action === "tap" || step.action === "click") {
      lines.push(`${xcuiExpression(selector)}.tap()`);
    } else if (step.action === "assertVisible") {
      lines.push(`XCTAssertTrue(${xcuiExpression(selector)}.waitForExistence(timeout: 30))`);
    }
  }
  return `${lines.join("\n")}\n`;
}

function xcuiExpression(selector) {
  if (selector.kind === "xcuiIdentifier") return `app.descendants(matching: .any).matching(identifier: "${swift(selector.value)}").firstMatch`;
  if (selector.kind === "xcuiLabel") return `app.staticTexts["${swift(selector.value)}"]`;
  if (selector.kind === "xcuiType") return `app.descendants(matching: .any).matching(NSPredicate(format: "elementType == %@", "${swift(selector.value)}")).firstMatch`;
  throw new Error(`unsupported_ios_selector ${JSON.stringify(selector)}`);
}

function swift(value) {
  return String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}
