#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/reports/android-ci
bash ./gradlew :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.quata.feature.feed.presentation.FeedRemoteRankingInstrumentedTest,com.quata.feature.official.presentation.OfficialRemoteRankingInstrumentedTest,com.quata.feature.official.presentation.OfficialDeepPaginationInstrumentedTest" \
  --no-daemon --stacktrace --console=plain \
  2>&1 | tee build/reports/android-ci/live-ranking-instrumentation.log

node scripts/verify-live-ranking-android-results.mjs \
  app/build/outputs/androidTest-results/connected/debug \
  | tee build/reports/android-ci/live-ranking-result.json
