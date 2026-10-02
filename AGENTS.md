# AGENTS.md — DeXWorkspaceTouch

## 1. Mission

- Luôn giải thích code và phản hồi bằng tiếng Việt.
- Các đoạn mã mẫu (code snippets) vẫn giữ nguyên cú pháp tiếng Anh.

Work on DeXWorkspaceTouch with the smallest practical Codex usage while preserving correctness, safety, and verifiability.

Primary objectives:

1. Stay strictly within the requested task scope.
2. Minimize unnecessary repository exploration.
3. Minimize model/tool round trips.
4. Avoid repeating successful checks.
5. Run the smallest sufficient test set first.
6. Stop when the requested PASS criteria are satisfied.
7. Never trade correctness for quota savings.

---

## Technical Autonomy

For DeXWorkspaceTouch development, Codex operates under the
[DWT Technical Autonomy Charter](docs/codex/DWT_TECHNICAL_AUTONOMY_CHARTER.md).
The charter grants technical execution autonomy within an approved task or
milestone scope.

Precedence:

1. Explicit current user/task instructions.
2. Safety/security/Git STOP boundaries.
3. AGENTS.md.
4. DWT Technical Autonomy Charter.
5. Historical plans/reports.

Current task/user constraints remain authoritative. AGENTS.md safety/security/Git
restrictions remain mandatory. The charter governs execution autonomy only where
those rules do not conflict; it cannot override explicit scope, licensing/signing
restrictions, destructive Git restrictions, release/publication authorization
requirements, or architecture STOP boundaries.

Codex should continue autonomously until a meaningful checkpoint or a charter
STOP boundary rather than requesting approval for every implementation detail.

---

## 2. Project Context

Android application:

- Package: `com.trancong.dexworkspacetouch`
- Kotlin
- Jetpack Compose
- Navigation
- Room
- ViewModel + StateFlow
- `viewModelScope`
- OkHttp
- Android Keystore
- minSdk 28
- target/compile SDK 37
- manual dependency injection

Important project areas include:

- DeX workspace/window launching
- Car mode/features
- License activation and runtime state
- Device identity
- Device proof-of-possession
- Signed license token verification
- License refresh
- Production release/update flow

License/security code is considered high-risk.

Do not modify licensing, cryptography, signing, release verification, production URLs, trusted keys, database migrations, or device-binding behavior unless the current task explicitly requires it.

---

## 3. Task IDs Are Authoritative

Tasks normally use IDs such as:

- `DWT-xxx`
- `LIC-xxx`
- `REL-xxx`
- `CAR-xxx`

The current task specification is authoritative.

If a task contains:

- Goal
- Scope
- Allowed files/modules
- Forbidden changes
- Verification
- PASS criteria

follow those constraints exactly.

Do not expand the task merely because adjacent code could be improved.

Do not perform opportunistic refactoring.

Do not clean unrelated code.

Do not upgrade dependencies unless explicitly required.

---

## 4. Quota-Efficient Repository Exploration

Before reading files, determine what information is actually required.

Prefer targeted searches such as:

- class name
- function name
- interface name
- route name
- resource ID
- test name
- error text
- task ID

Avoid exploring entire directories when a targeted search is sufficient.

### Batch independent reads

When several known files must be inspected, read/search them together where practical.

Prefer:

```text
Find A + B + C
→ inspect relevant sections
→ form implementation plan
```

instead of:

```text
Find A
→ reason
→ find B
→ reason
→ find C
→ reason
```

when those operations are independent.

### Do not repeatedly reread files

Once sufficient context from a file has been obtained:

- retain that context;
- do not reread unchanged sections;
- reread only if the file changed or new evidence requires it.

### Avoid broad repository scans

Do not recursively inspect the entire repository unless:

- architecture is genuinely unknown;
- the task spans multiple unknown modules;
- targeted searches failed.

---

## 5. Plan Before Editing

For non-trivial tasks, create a compact internal implementation plan before editing.

The plan should identify only:

- files likely to change;
- behavior being changed;
- tests required;
- major risks.

Do not produce long speculative designs unless requested.

If the requested implementation is already clear, begin implementation rather than spending multiple turns discussing alternatives.

---

## 6. Scope Lock

Before modifying code, establish a scope lock.

Example:

```text
Task: LIC-015

Allowed:
- license diagnostics
- related unit tests

Do not change:
- token format
- signing algorithm
- backend API contract
- activation semantics
- database schema
```

During implementation, if an unrelated problem is discovered:

1. Do not fix it automatically.
2. Mention it in the final report if relevant.
3. Continue the requested task unless it blocks PASS.

---

## 7. Editing Strategy

Prefer the smallest coherent patch.

Do not rewrite complete files when a localized modification is sufficient.

Do not rename public APIs unless necessary.

Do not change formatting across unrelated code.

Do not introduce abstractions solely for theoretical cleanliness.

Reuse existing project patterns before creating new architecture.

When the project already contains:

- repository patterns
- coordinator patterns
- ViewModel state patterns
- result/error types
- test helpers

prefer those established patterns.

---

## 8. Quota-Efficient Tool Usage

Batch independent read-only operations whenever practical.

Examples:

```text
GOOD

search symbols A/B/C together
read related files together
inspect related tests together
```

Avoid unnecessary sequences of tiny tool calls.

However, keep state-changing operations sequential when order matters.

Examples:

```text
edit
→ compile
→ test
```

Do not parallelize operations that can interfere with each other.

---

## 9. Subagents

Do not spawn subagents by default.

Use a subagent only when:

- the task has genuinely independent large workstreams;
- parallel investigation provides clear value;
- duplicated context cost is justified.

Do not use subagents for:

- a small bug fix;
- one feature touching a few files;
- basic repository discovery;
- routine testing;
- simple refactoring.

Prefer one well-informed agent over several agents rediscovering the same project context.

---

## 10. Test Strategy

Use progressive verification.

### Level 1 — targeted verification

Run the smallest test/build command that directly exercises the changed behavior.

Examples:

- affected unit-test class
- affected module tests
- compile Kotlin
- relevant instrumentation test

If Level 1 fails:

- diagnose the failure;
- modify only what is necessary;
- rerun the failed/relevant checks.

Do not rerun unrelated suites after every edit.

### Level 2 — affected-area regression

Run broader tests when:

- the change crosses multiple components;
- shared behavior changed;
- targeted tests reveal a regression risk;
- the task explicitly requires them.

### Level 3 — full regression

Run the full project suite only when justified by:

- release task;
- security/license change;
- database migration;
- shared architecture change;
- explicit PASS criteria;
- significant cross-module change.

Do not run the complete suite repeatedly when nothing relevant changed after the previous PASS.

---

## 11. Test Reuse

A successful test result remains valid unless later edits affect its assumptions.

Example:

```text
Test A PASS
Test B PASS

edit unrelated documentation

Do NOT rerun Test A and Test B.
```

Example:

```text
Test A PASS

edit production code covered by Test A

Rerun Test A.
```

Do not rerun tests simply to make the final report look more complete.

---

## 12. Build Output

Keep command output concise whenever possible.

Prefer:

- failure summary
- relevant stack trace
- final PASS/FAIL result

Avoid repeatedly dumping:

- full Gradle logs;
- dependency lists;
- unchanged warnings;
- complete test output.

Inspect detailed logs only when required for diagnosis.

---

## 13. Debugging

When a failure occurs:

1. Identify the first meaningful failure.
2. Trace it to the smallest likely cause.
3. Inspect only relevant code.
4. Apply the smallest viable fix.
5. Rerun the failed check.
6. Expand investigation only if the failure persists.

Do not immediately perform broad architecture exploration for a localized failure.

Do not change multiple unrelated hypotheses simultaneously.

---

## 14. Android Device / Instrumentation Work

Do not perform repeated device checks without a reason.

When device testing is required:

1. Confirm device availability once.
2. Install/build only the required APK.
3. Run the specified scenario.
4. Capture the relevant evidence.
5. Stop once PASS is established.

Do not repeatedly query:

- device properties
- package list
- Android version
- display state

unless they may have changed or are directly relevant.

---

## 15. License/Security Changes

For `LIC-*` tasks, prioritize correctness over quota reduction.

Before changing security-sensitive behavior, determine exactly which layer is involved:

```text
Android
Backend
Protocol
Crypto
Database
Production configuration
```

Do not modify multiple layers unless required.

Protect existing invariants such as:

- fail-closed behavior;
- exact trusted-key matching;
- signature verification;
- device binding;
- challenge consumption;
- replay protection;
- token claim validation;
- production/debug separation.

Security checks required by the task must not be skipped to save quota.

---

## 16. Git

Use Git as verification context, not as an excuse for excessive repository inspection.

Before changes, inspect:

```bash
git status
```

Avoid modifying pre-existing unrelated user changes.

After implementation, inspect the scoped diff.

Prefer:

```bash
git diff -- <changed files>
```

before using a repository-wide diff when the affected files are known.

Do not:

- reset user changes;
- clean untracked files;
- force checkout;
- amend unrelated commits;
- force push

unless explicitly instructed.

---

## 17. No Repeated Confirmation

If the task specification is sufficiently clear, execute it.

Do not repeatedly ask the user to confirm:

- implementation details already specified;
- obvious continuation steps;
- running required tests;
- fixing failures clearly caused by the current patch.

When several valid implementation choices exist, prefer the smallest solution consistent with existing project architecture.

---

## 18. Stop Conditions

Stop implementation when all of the following are true:

1. Requested behavior is implemented.
2. Scope constraints are respected.
3. Required tests/builds PASS.
4. No known blocker remains within task scope.
5. PASS criteria are satisfied.

Do not continue with:

- optional refactoring;
- extra cleanup;
- unrelated warnings;
- speculative enhancements;
- additional test repetitions.

A completed task should remain completed.

---

## 19. Final Report

Keep the final report concise and evidence-based.

Use this structure:

```text
<TASK-ID> completed.

Changed
- ...
- ...

Verification
- <test/build>: PASS
- <test/build>: PASS

PASS criteria
- <criterion>: PASS
- <criterion>: PASS

Not changed
- ...

Notes
- only blockers or important observations
```

Do not provide a long narrative of every command executed.

Do not report PASS unless there is actual evidence.

If something could not be verified, explicitly state:

```text
NOT VERIFIED
```

rather than inferring success.

---

## 20. Preferred Task Format

When receiving a new task, interpret this format directly:

```text
TASK-ID: DWT-xxx

Goal
<one concrete goal>

Scope
- allowed behavior/file/module
- allowed behavior/file/module

Do not change
- unrelated subsystem
- database/API/security behavior unless required

Implementation requirements
1. ...
2. ...
3. ...

Verification
- targeted test
- build/test requirement

PASS
- observable criterion
- observable criterion
```

Do not rewrite this specification into a larger project unless necessary.

---

## 21. Quota-Saving Priority Order

When multiple valid approaches exist, prefer this order:

1. Use existing knowledge from the current task/session.
2. Targeted symbol/file search.
3. Read only relevant code.
4. Apply smallest coherent patch.
5. Run targeted verification.
6. Expand tests only when justified.
7. Expand repository exploration only when blocked.
8. Use subagents only for genuinely independent large workstreams.

---

## 22. Core Rule

The goal is not to minimize commands at all costs.

The goal is to minimize **unnecessary reasoning cycles, repeated context acquisition, redundant tests, and unrelated work** while still producing a correct and verifiable patch.

Correctness > quota savings.

Within equally correct approaches:

**choose the one requiring less exploration, fewer model cycles, fewer redundant tool calls, and less repeated verification.**