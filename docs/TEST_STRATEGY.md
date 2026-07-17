# Test Strategy

## Three sources of truth

1. JVM tests are the source of truth for deterministic domain and workflow logic. They use fixed
   clocks, IDs, catalogs, repositories, launchers, and delays; they never call PackageManager or wait
   for wall-clock time.
2. Android instrumentation tests are the source of truth for Room/SQLite runtime behavior. Golden
   persistence tests use a file-backed database for close/reopen coverage and remove it after every
   case.
3. Physical devices are the source of truth for Samsung DeX displays, taskbar/work-area calculation,
   window resize, process behavior, and actual multi-app launch. An emulator does not replace either
   the S23 Ultra or the Note 8 legacy-ROM device.

## Golden regression scope

- `golden/GoldenWorkspaceWorkflowTest` connects create, Designer changes/history, App Picker identity,
  Library persistence/recreation, launch readiness, sequencing, and result mapping.
- `golden/GoldenWorkspacePersistenceTest` covers file-backed insert/update/rename/delete/reopen,
  malformed-row isolation, Unicode/large JSON, ordering, and failed-update rollback.
- Existing focused class tests remain the fastest diagnosis layer and are not duplicated wholesale.
- Compose animation, exact pixels, DeX taskbar, display disconnect, and real PackageManager/launch are
  intentionally manual because device and window-manager behavior is authoritative.

## Stability rules

- No real time, UUID, random identity, PackageManager, or coroutine delay in JVM golden tests.
- Assertions describe behavior rather than private implementation.
- No screenshot golden baseline across API levels or densities.
- Performance tests assert correctness at scale; absolute device timing stays in measured reports.
