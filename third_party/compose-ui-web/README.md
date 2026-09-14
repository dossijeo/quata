# Compose Web semantics owner restoration

Focal backport for FLOW-DEEP-LINKS. Compose UI 1.10.0 loses the active accessibility
owner when a temporary dialog closes. Qüata's common UGC gate exposed this defect:
the canvas showed Chat while the browser retained obsolete dialog semantics.

Only `ComposeWebSemanticsListener.kt` is changed: retain live owners, restore the
last owner, invalidate on append/remove, and remove node/parent caches together
with DOM nodes. No public API, alternate HTML tree, modality or product flow changes.
`owner-restoration.patch` identifies the modified upstream file. Upstream copyright
headers remain in place; the code and derivative binary use the included Apache 2.0
license. The regression test is provided under the same license.

## Origin and consumption

- Repository: https://github.com/JetBrains/compose-multiplatform-core
- Tag: `v1.10.0`; exact commit: `b69b7202e3bcecf55cf1467821b529247bf6b043`.
- Build: upstream Gradle wrapper 8.13, Kotlin compiler 2.2.20, Wasm JS target.
- Local coordinate: `org.jetbrains.compose.ui:ui:1.10.0-quata-owner.1`.
- Klib SHA256: `f79203757b133aacd3ffad129333c9b0daa79d5afe04d24b360ee4cc1ab43911`.
- Official comparison Klib SHA256: `c4fed8f4ee36d96aa6316cf0c27e205893b301c2be1f0d1137145e1990e1a299`.

The committed Maven descriptor derives from
https://repo.maven.apache.org/maven2/org/jetbrains/compose/ui/ui-wasm-js/1.10.0/ui-wasm-js-1.10.0.module.
It preserves API/runtime attributes and dependency versions, changes the component
to the explicit local version and pins the actual binary's size/checksums. It omits
the upstream source variant rather than mislabel unpatched sources. Source is
reconstructible from the exact upstream revision and the patch in this directory.

`gradle/compose-ui-web-backport.gradle.kts` substitutes only Wasm configurations
requesting UI 1.10.0. All modules of Qüata's Wasm executable therefore consume it;
Android/iOS remain on the official artifacts. Both Klib and descriptor are checked
by SHA256 before Wasm resolution. The local repository exposes only this coordinate.
Builds do not depend on an ignored directory, Maven Local, modified Gradle caches,
a developer init script or an upstream build on CI.

When introducing or replacing this backport, regenerate the complete Qüata Wasm
distribution before collecting acceptance evidence:

```sh
./gradlew :web:wasmJsBrowserDevelopmentExecutableDistribution \
  --rerun-tasks --no-build-cache -Pkotlin.incremental=false --max-workers=2
```

For final production evidence use `:web:wasmJsBrowserDistribution` with the same
rebuild flags. Keep the resulting distribution fingerprint with the evidence.
The first local build that reused outputs threw `illegal cast` on Feed detail
exit; rebuilding all 158 tasks with these flags fixed that exact case without
changing sources or the Klib. Both incremental compilation and cache/task reuse
changed, so neither is independently proven responsible. A subsequent normal
build retained the working bundle. Incremental compilation remains enabled for
ordinary development; do not reuse the earlier failing bundle as acceptance.

Rebuilding all 54 upstream tasks with `--rerun-tasks` reproduced the pinned Klib
byte for byte. This verifies the current build environment; hashes still must be
checked when rebuilding elsewhere. The local coordinate is exclusive to the
vendored repository, so a public repository cannot supply a homonymous artifact.

## Rebuilding and regression tests

Use a separate upstream checkout; do not build upstream inside Qüata's source tree
or replace the official dependency cache. In the commands below, `QUATA` denotes an
absolute path to this checkout and `UPSTREAM` a fresh destination. Use `gradlew.bat`
on Windows. Enable Git long paths on Windows before checking out upstream.

```sh
git clone --branch v1.10.0 --depth 1 https://github.com/JetBrains/compose-multiplatform-core.git UPSTREAM
cd UPSTREAM
git rev-parse HEAD
# Must equal b69b7202e3bcecf55cf1467821b529247bf6b043; stop otherwise.
git apply --check QUATA/third_party/compose-ui-web/owner-restoration.patch
git apply QUATA/third_party/compose-ui-web/owner-restoration.patch
./gradlew :compose:ui:ui:wasmJsJar -Pcompose.platforms=wasmJs --max-workers=2
```

Output: `out/androidx/compose/ui/ui/build/libs/ui-wasm-js-9999.0.0-SNAPSHOT.klib`.
The upstream snapshot filename is not the version used by Qüata. Compare its hash
and manifest with the pinned artifact before replacing anything. ABI 2.2.0,
metadata 1.4.1, compiler 2.2.20, `unique_name=org.jetbrains.compose.ui:ui` and the
dependency list must remain compatible. A differing rebuild is a review item,
never permission to silently refresh the pin.

Copy `OwnerRestorationTest.kt` to upstream's
`compose/ui/ui/src/webTest/kotlin/androidx/compose/ui/platform/a11y/`. With Chrome
available and `jetbrains.androidx.web.tests.enableChrome=true` in the environment:

```sh
./gradlew :compose:ui:ui:wasmJsBrowserTest \
  --tests '*OwnerRestorationTest*' --tests '*CfWA11YTest.a11yButtonClick*' \
  -Pcompose.platforms=wasmJs --no-configure-on-demand --max-workers=2
```

For headless environments set `CHROME_BIN` and add an upstream-local Karma config
containing `config.customLaunchers.ChromeForComposeTests.base = "ChromeHeadless";`.
The recorded run used Node 22.0.0 and Yarn 1.22.17 already installed locally after
the upstream download service failed. This provisioning workaround is independent
of the listener and is not required by Qüata builds.

The three tests cover two root → dialog → root cycles and accessible actions,
removal of an intermediate layer while the top continues updating, and removal
of the last owner followed by a new root on the same listener. The upstream button
test is the control. Original listener: two restoration failures, control passes;
patched listener: all four pass. Two Qüata hermetic browser runs also passed the
checking → accepted → Chat transition. These are synthetic interaction evidence,
not real backend or complete FLOW-DEEP-LINKS acceptance.

## Removal

When an official Compose version fixes these cases, run the same regressions and
Qüata acceptance before removing the backport, repository and resolution rule.
Do not upgrade unrelated components merely to remove this directory. Replacing
this binary changes the Wasm product and requires new affected evidence.
