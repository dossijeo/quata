# CI lanes and merge policy

Every pull request runs the fast checks (**PR fast contracts and focal
imports** and **iOS fast contracts**). They validate workflow contracts and
the diff, then compile a focused Wasm import set. They are diagnostic coverage,
not release certification.

The expensive final checks run on every `main`/`master` push and manual
dispatch. For a pull request they run only after the `candidate-final` label
is present; `pull_request` listens to both `labeled` and `synchronize`, so a
new commit on a labelled candidate restarts the final lane.

Final checks are deliberately named separately:

- **Web/Wasm final distribution and Chrome smoke** covers the full Wasm test
  matrix, production distribution and browser smoke.
- **Kotlin iOS final host, simulator and archive** covers Kotlin iOS targets,
  XCFramework, Swift host, simulator contracts and the unsigned archive.
- CodeQL remains its own required security check.

The required status checks are **Web/Android final certification gate**,
**iOS final certification gate**, and CodeQL. Each gate always runs and fails
closed unless its `candidate-final` PR has completed every final job
successfully. A skipped, cancelled or failed final job is therefore never
green evidence. The merge manager applies `candidate-final` only once the diff
is frozen, waits for both gates on that exact head SHA, then merges or removes
the label after a material change.

Both workflows deliberately have no `paths` filter: every pull request reaches
the fast checks and their fail-closed gate, while every `main`/`master` push
and manual dispatch runs the complete lane. This also means a `labeled` or
`synchronize` event for `candidate-final` cannot be lost because its diff falls
outside an old route allow-list.

Concurrency is scoped by pull-request number and cancels only superseded PR
runs. Push and manual-dispatch runs use stable event/ref groups and are never
cancelled, preserving their diagnostic evidence.

`wasm-baseline-capture.yml` still contains a separate, pre-existing actionlint
compatibility finding and is intentionally outside this lane-policy change.
