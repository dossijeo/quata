# CI lanes and merge policy

Every pull request runs the fast checks (**PR fast contracts and focal
imports** and **iOS fast contracts**). They validate workflow contracts and
the diff, then compile a focused Wasm import set. They are diagnostic coverage,
not release certification.

Before any expensive job starts, `scripts/classify-ci-impact.mjs` classifies
the complete diff as Web, Android, iOS, any combination of them, or
documentation-only. Platform source sets and operational files select only
their consumers; common source, shared build logic, capabilities and workflow
control files select all consumers. Any unknown non-documentation path also
selects all consumers, so a missing rule costs time but can never create a
false green.

The expensive final checks run only for the platforms selected by that
classifier. Manual dispatch deliberately selects every platform. For a pull
request the selected jobs additionally require the `candidate-final` label;
`pull_request` listens to both `labeled` and `synchronize`, so a new commit on
a labelled candidate restarts the final lane.

Final jobs and gates are deliberately named separately:

- **Web/Wasm final distribution and Chrome smoke** covers the full Wasm test
  matrix, production distribution and browser smoke.
- **Kotlin iOS final host, simulator and archive** covers Kotlin iOS targets,
  XCFramework, Swift host, simulator contracts and the unsigned archive.
- **Analyze java-kotlin** and **Analyze javascript-typescript** perform the
  real CodeQL scans when the diff is not documentation-only.
- **CodeQL final security gate** is the required security check. It always
  appears, passes documentation-only PRs explicitly, and otherwise fails closed
  unless the CodeQL matrix completed successfully.

The required status checks are **PR fast contracts and focal imports**,
**iOS fast contracts**, **Web/Android final certification gate**,
**iOS final certification gate**, and **CodeQL final security gate**. The fast
checks expose classifier or workflow-contract mistakes without waiting for
expensive runners. Each final gate always runs and fails closed unless its
`candidate-final` PR has completed every affected final job successfully and
every unaffected job was actually skipped. Thus a classifier mistake,
cancellation or failure is never green evidence.

Promotion to `candidate-final` is also the authorization for GitHub native
auto-merge on the frozen head SHA. Use
`node scripts/promote-candidate-final.mjs --pr <number> --sha <head-sha>` after
local preflight and evidence/attestation are complete. The script verifies that
the PR is not a draft, the current head still equals the frozen SHA, the stable
required gates exist, repository auto-merge is enabled, applies
`candidate-final` if needed, and requests native auto-merge with the repository's
operational merge method. It does not merge manually and it never bypasses branch
protection.

Both workflows deliberately have no `paths` filter: every pull request reaches
the classifier, fast contracts and fail-closed gate, while selected platform
jobs are omitted inside the workflow. This also means a `labeled` or
`synchronize` event for `candidate-final` cannot be lost because its diff falls
outside an old route allow-list.

Concurrency is scoped by pull-request number and cancels only superseded PR
runs. Push and manual-dispatch runs use stable event/ref groups and are never
cancelled, preserving their diagnostic evidence.

`wasm-baseline-capture.yml` still contains a separate, pre-existing actionlint
compatibility finding and is intentionally outside this lane-policy change.

## Defectos escapados del preflight local

El head `3a94c9c639ebe9af7534aab27e6ec23ff8f32094` de la PR 169 quedó
invalidado por un defecto de preflight local: el contrato
`ios-public-simulator-matrix-contract.test.mjs` seguía exigiendo entradas del
antiguo filtro `paths`, aunque la política aprobada es ejecutar ambos workflows
en cada PR y push protegido. El contrato estaba en el paso remoto rápido de
iOS, pero no en la ejecución focal local previa. La corrección exige
explícitamente la ausencia de `paths`, incorpora ese contrato a
`test:ci-fast-contracts` (réplica local de los contratos rápidos remotos) y a
la suite agregada `test:web-wave2-contracts` ejecutada antes de push.
