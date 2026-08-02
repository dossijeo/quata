# CI lanes and merge policy

Every pull request runs the required **PR fast contracts and focal imports**
check. It validates workflow contracts and the diff, then compiles a focused
Wasm import set. It is diagnostic coverage, not release certification.

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

Repository rulesets must require the fast check, both final checks and CodeQL
before merge. A skipped final job on an unlabelled pull request is **not**
evidence and must never satisfy that rule. The merge manager applies
`candidate-final` only once the diff is frozen, waits for the final checks on
that exact head SHA, then merges or removes the label after a material change.

Concurrency is scoped by pull-request number and cancels only superseded PR
runs. Push and manual-dispatch runs use stable event/ref groups and are never
cancelled, preserving their diagnostic evidence.
