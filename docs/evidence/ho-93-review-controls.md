# HO-93 Review Controls

Date: 17.11.2026

The HO-10 isolation documents under `specs/ho-10-household-isolation/` are a manually prepared draft for one AC-14 scenario. They are not an approved specification or a complete HO-10 plan.

The review guidance requires concrete findings, positive and negative scenarios, observable outcomes, and an evidence mapping for risk-scoped changes. The draft plan describes a temporary authorization-check removal to verify test sensitivity when HO-10 is implemented.

## Clean-session discovery

On 17.11.2026, a fresh `codex exec --sandbox read-only` session was started in the clean detached worktree `ho93-validation` at revision `a7b0914` with Codex CLI `0.167.0-alpha.3.2`. The session reported the repository's scope, review, verification and handoff instructions from its initial context and listed all nine `speckit-*` skills. It exited successfully without editing files. This revision predates the uncommitted review-rule additions on the HO-93 branch, so their discovery remains to be checked after they are committed.

In the same worktree, `SPECIFY_FEATURE_DIRECTORY=specs/ho-10-household-isolation SPECIFY_FEATURE_NO_PERSIST=1 .specify/scripts/bash/check-prerequisites.sh --paths-only` returned the requested feature directory and its spec, plan and tasks paths. This was a path resolution check; it did not create those artifacts or run the feature workflow.

## Document control exercise

On the HO-93 branch based on `a7b0914`, a read-only Python check verified that the draft spec states foreign-household rejection and unchanged committed state and that the task list includes an assertion for those outcomes. The same process made two in-memory copies, removed the rejection sentence from one and the task assertion from the other, and confirmed the corresponding explicit string checks failed. The files were not modified by the exercise.

This exercise verifies the specific string checks used in this manual control only. A separate read-only Spec Kit control run is documented in [HO-93 verification](ho-93-verification.md).

## Spec Kit preparation exercise

On 17.11.2026, the clean detached `ho93-validation` worktree at `a7b09141fdcaef14b2eff064cb21f66b911ea9ed` was used for one HO-10 AC-14 scenario. A Codex session ran specify, clarify, plan, checklist, tasks and read-only analyze with `SPECIFY_FEATURE_DIRECTORY=specs/ho-10-household-isolation` and `SPECIFY_FEATURE_NO_PERSIST=1`. The session was interrupted during checklist and resumed; its first resumed checklist write failed due to an undefined path, then succeeded using the explicit path.

The run produced nine draft Markdown documents, including spec, plan, tasks and two checklists. The built-in requirements checklist has two unresolved items, and the reviewer checklist remains unchecked. The read-only analysis mapped four requirements and four success criteria to tasks. It identified unresolved operation selection, response comparison and committed-state evidence, plus documentation inconsistencies. The task assertions and artifact reconciliation were improved, the checkout's `dev/sharedops` package replaced the outdated Linear path as a proposed test location, and a future isolated test-sensitivity experiment was restored from the earlier manual draft. A subsequent analysis confirmed the path attribution and identified one sequencing ambiguity. The quickstart now places that experiment before final verification, matching the tasks. A fresh prerequisite check resolved the selected directory, and an explicit ordering assertion passed.

The validation worktree retains these untracked artifacts. After comparison with the initial manual draft, the HO-93 working tree contains the generated set, preserving the manual draft's future test-sensitivity control and conditional implementation task. The reconciled set covers only the AC-14 slice and awaits owner review and decisions for full HO-10. No implementation, product tests or runtime checks were run. The separate controlled omission run is documented in the verification record.

No HO-10 implementation or PostgreSQL integration test was executed. The test-sensitivity experiment remains a planned HO-10 task. For the eventual evidence, record each applicable criterion, test, command, result, tested revision, and blocked or skipped mandatory checks.
