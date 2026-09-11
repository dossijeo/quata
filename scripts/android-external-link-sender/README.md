# External Android App Link test sender

Standalone Gradle build, deliberately absent from the product settings/dependencies.
Its own application ID and instrumentation UID submit one implicit HTTPS ACTION_VIEW
with CATEGORY_BROWSABLE. PackageManager must resolve it publicly to Qüata MainActivity;
the emitted Intent has neither package nor component. UIAutomator only observes.
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

References: [implicit intents](https://developer.android.com/training/basics/intents),
[PackageManager resolution](https://developer.android.com/reference/android/content/pm/PackageManager),
[UI Automator instrumentation](https://developer.android.com/training/testing/other-components/ui-automator-legacy).
