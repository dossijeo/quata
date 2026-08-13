#!/usr/bin/env node

import { readFileSync } from "node:fs";

const runners = [
  "scripts/chat-actions-notifications-web-evidence.mjs",
  "scripts/chat-actions-notifications-android-evidence.mjs",
  "scripts/chat-actions-notifications-ios-evidence.mjs",
];

const before = {
  validWavFixtureImplementations: 3,
  createChatAttachmentMessageImplementations: 3,
  storageCleanupImplementations: 3,
  docsOnlyWebAndroidJobs: ["classify-impact", "fast-contracts", "final-certification-gate"],
  docsOnlyIosJobs: ["classify-impact", "ios-fast-contracts", "ios-final-certification-gate"],
  docsOnlyCodeQlJobs: ["analyze java-kotlin", "analyze javascript-typescript"],
  heavyDocsOnlySetups: ["setup-java", "setup-gradle", "Android SDK", "CodeQL init", "CodeQL Java build"],
};

function count(pattern, text) {
  return (text.match(pattern) ?? []).length;
}

const runnerText = runners.map((path) => readFileSync(path, "utf8")).join("\n");
const shared = readFileSync("scripts/e2e-fixtures/chat-attachments.mjs", "utf8");
const webAndroid = readFileSync(".github/workflows/web-android-pr.yml", "utf8");
const ios = readFileSync(".github/workflows/ios-build.yml", "utf8");
const codeql = readFileSync(".github/workflows/codeql.yml", "utf8");

const after = {
  validWavFixtureImplementations: count(/function validWavFixture\(/g, runnerText) + count(/function validWavFixture\(/g, shared),
  createChatAttachmentMessageBackendImplementations: count(/function seedChatAttachmentFixture\(/g, shared),
  createChatAttachmentMessagePlatformWrappers: count(/function createChatAttachmentMessage\(/g, runnerText),
  sharedSeedChatAttachmentFixtureImplementations: count(/function seedChatAttachmentFixture\(/g, shared),
  storageCleanupImplementations: count(/function createCleanupRegistry\(/g, shared),
  storageCleanupRegistryUses: count(/createCleanupRegistry\(\)/g, runnerText),
  docsOnlyWebAndroidJobs: webAndroid.includes("needs.classify-impact.outputs.docs_only != 'true'")
    ? ["classify-impact", "final-certification-gate"]
    : before.docsOnlyWebAndroidJobs,
  docsOnlyIosJobs: ios.includes("needs.classify-impact.outputs.docs_only != 'true'")
    ? ["classify-impact", "ios-final-certification-gate"]
    : before.docsOnlyIosJobs,
  docsOnlyCodeQlJobs: codeql.includes("needs.classify-impact.outputs.docs_only != 'true'")
    ? ["classify-impact"]
    : before.docsOnlyCodeQlJobs,
  heavyDocsOnlySetupsAvoided: ["setup-java", "setup-gradle", "Android SDK", "CodeQL init", "CodeQL Java build"],
};

process.stdout.write(`${JSON.stringify({ before, after }, null, 2)}\n`);
