# External Android App Link test sender

Standalone Gradle build, deliberately absent from the product settings/dependencies.
Its own application ID and instrumentation UID submit one implicit HTTPS ACTION_VIEW
with CATEGORY_BROWSABLE. PackageManager must resolve it publicly to Qüata MainActivity;
the emitted Intent has neither package nor component. UIAutomator only observes Qüata.
No shell activity launch is used. The test app has no launcher activity.

Build with the repository wrapper and `-p scripts/android-external-link-sender`
(`assembleDebug assembleDebugAndroidTest`). Run through standard instrumentation or
`connectedDebugAndroidTest`, selecting `PublicLinkTest#deliverPublicLink` and supplying
unique `runId` and public `publicUrl` arguments.
Reports/screenshots are stored in the sender's external files directory. Do not rerun
an uncertain delivery or reuse a run ID. A passed delivery probe alone does not certify
the exact destination, cold/warm state, exit or consumption: those require the focal
observer and host process evidence. Never pass session credentials in arguments.

`PublicLinkTest#observeCurrentAndBack` emits no Intent. It requires its own `runId`,
`expectedResource` and `expectedText`; `dismissStartupPrompt=true` requires the known
first-run App Links prompt before closing it. UIAutomator only reads/captures. BACK
uses the supported UiAutomation input API from instrumentation, not a shell command.
Validate JUnit outcomes: an instrumentation transport exit code of zero alone is insufficient.

`PublicLinkTest#acknowledgeSystemUiAnr` is separate environment recovery, never part of
link delivery. It requires Android's exact "System UI isn't responding" dialog and
selects its "Wait" action once. It neither starts nor interacts with Qüata. Record any
such recovery and require a healthy launcher before a new cold run. Boot completion
alone does not prove launcher readiness or absence of a background product process.

The optional session custody instrumentation is test-only and is not the sender.
Build it with `:app:assembleDebugAndroidTest -PquataDeepLinkCustody=true`; this selects
`DeepLinkSessionCustodyRunner` only for the test APK. Normal test builds retain their
default runner. Its passive Application prevents product startup/session observers.
`scripts/e2e-fixtures/chat-deep-link-android-session-step.mjs` transports a single private
request through an owned local socket, never argv, and requires the exact receipt plus
terminal JUnit success. `probe-empty` reads raw empty preferences; install/clear require
exclusive AVD ownership and durable private journal integration before real use.
The adapter connects to the shared fixture coordinator through
`scripts/flow-deep-links-android.mjs`. Installed APK identity is verified before the
first custody probe; each owned ADB forward is recorded and its removal verified.
An unresolved cleanup preserves the lease and journals. A synthetic guard test and
empty probe do not certify authenticated Chat or native login; real trial results
still require inspection of the captured product UI.

References: [implicit intents](https://developer.android.com/training/basics/intents),
[PackageManager resolution](https://developer.android.com/reference/android/content/pm/PackageManager),
[UI Automator instrumentation](https://developer.android.com/training/testing/other-components/ui-automator-legacy).
