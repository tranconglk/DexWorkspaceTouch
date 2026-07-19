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
- Room migration instrumentation validates v1→v2 row preservation, the default unpinned value,
  schema identity, and pin persistence after reopening the migrated database.
- Pin JVM coverage verifies mapping, repository field updates, partition/sort/search behavior,
  ViewModel pin/unpin, duplicate defaults, and preservation through rename/Designer save.
- Compose animation, exact pixels, DeX taskbar, display disconnect, and real PackageManager/launch are
  intentionally manual because device and window-manager behavior is authoritative.

## Stability rules

- No real time, UUID, random identity, PackageManager, or coroutine delay in JVM golden tests.
- Assertions describe behavior rather than private implementation.
- No screenshot golden baseline across API levels or densities.
- Performance tests assert correctness at scale; absolute device timing stays in measured reports.
## Workspace transfer

- JVM tests cover deterministic envelopes, strict decoding, size limits, Unicode naming and canvas reuse.
- Instrumentation covers FileProvider and Room persistence seams without opening a real Sharesheet target.
- Manual cross-device validation records provider MIME behavior for Quick Share, Files, Drive and Zalo.
- Corrupted, oversized and newer-version `.dwt` files must fail before preview or persistence.
- External ACTION_VIEW JVM coverage includes content sniffing independent of MIME/extension, strict
  size/version failures, stream closure, duplicate/consumed events, and pending Designer routing.
- Instrumentation verifies both custom MIME filters resolve to MainActivity and the granted content
  URI is readable. Device validation remains authoritative for provider-reported MIME and cold/warm
  chooser behavior.
- Provider matrix records Samsung My Files, Google Files/Downloads, Quick Share, Drive, Zalo and
  Bluetooth where available. Add an octet-stream fallback only after this evidence proves it is
  needed; content validation remains mandatory.
- Measured 2026-07-19: Downloads on the Note 8 legacy ROM delivered a received `.dwt` document as
  `application/octet-stream`; the compatibility filter is covered by instrumentation and every
  payload still passes the same bounded envelope detector.
- ACTION_SEND helper tests cover EXTRA_STREAM, ClipData-only, identical and conflicting sources,
  missing/multiple URI, unsupported action, content-only scheme, replay/reshare, and bounded input.
- Instrumentation verifies all three registered SEND MIME types resolve to MainActivity and that
  EXTRA_STREAM/ClipData content URIs are adapted and readable. Samsung Sharesheet selection itself
  remains a physical-device/manual assertion.
- Measured 2026-07-19 on S23 Ultra and Note 8 legacy ROM: Samsung My Files shared both `.dwt` and
  `.dwtbundle` as `application/octet-stream`, supplied the same content URI in EXTRA_STREAM and one
  ClipData item, and set `FLAG_GRANT_READ_URI_PERMISSION`. Detection returned `SingleWorkspace` and
  `LibraryBundle` respectively; both existing preview/confirmation flows completed successfully.
## Workspace Library icon and metadata regression

- Kiểm tra LRU 64, eviction, cache hit, cached fallback và stable package/activity key.
- Với dữ liệu 100 workspace, cuộn toàn Library trên S23 Ultra và Note 8; theo dõi crash/OOM,
  icon tải lại bất thường và missing-app fallback.
- Metadata, relative time và snapshot phải giữ đúng sau recreation-style reload.
- Cross-device backup matrix covers S23 Ultra ↔ Note 8, 100-workspace bundles, malformed/versioned
  files, restoring the same bundle twice and atomic rollback on a conflicting row.
