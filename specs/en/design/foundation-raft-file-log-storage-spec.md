<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Nacos Raft File Log Storage Spec

This document expands the file log backend in the
[CP Consistency Spec](foundation-cp-consistency-spec.md). It defines configuration,
file formats, local publication, recovery, failure states, and verification requirements.

## 1. Scope

The file backend implements JRaft's `LogStorage` interface for one Raft group.
Segments hold log records, the Manifest publishes the locally retained range,
and indexes provide rebuildable record locations. Local publication is distinct
from Raft majority commit. Committed indexes, state-machine apply, and snapshot
semantics remain owned by the CP protocol and domain specs.

`FileJRaftServiceFactory` creates only `FileLogStorage`; metadata storage,
snapshot storage, and the LogEntry codec continue to delegate to
`DefaultJRaftServiceFactory`.

## 2. Configuration And Backend Selection

The `data` map in [RaftConfig](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/RaftConfig.java)
receives `nacos.core.protocol.raft.data.*` properties. Defaults are defined in
[RaftSysConstants](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/RaftSysConstants.java).
[RaftOptionsBuilder](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/utils/RaftOptionsBuilder.java)
sets synchronization options on `RaftOptions`.

| Property | Default | Behavior |
| --- | --- | --- |
| `nacos.core.protocol.raft.data.log_storage_type` | `rocksdb` | Selects `rocksdb` or `file`, trimming whitespace and ignoring case; empty, blank, or unsupported values throw `IllegalArgumentException`. |
| `nacos.core.protocol.raft.data.sync` | `true` | Controls synchronous log writes; the file backend requires true. |
| `nacos.core.protocol.raft.data.sync_meta` | `false` | Passed to the default metadata store; does not relax the file backend's `sync=true` requirement. |

File backend configuration:

```properties
nacos.core.protocol.raft.data.log_storage_type=file
nacos.core.protocol.raft.data.sync=true
```

Templates are provided in
[bootstrap](../../../bootstrap/src/main/resources/application.properties) and
[distribution](../../../distribution/conf/application.properties).

[JRaftServer.init()](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/JRaftServer.java)
selects the factory and checks synchronization options before starting
RaftExecutor. The server instance retains the factory and explicitly sets it on
the copied `NodeOptions` when creating a group. Backend selection is not reread
at runtime.

The default is `rocksdb`; explicit `file` selection uses `FileJRaftServiceFactory`.
Every group must receive the factory selected at initialization, including after
copying `NodeOptions`. Invalid configuration or `sync=false` for the file backend
must fail before the Raft runtime starts. Runtime hot switching is unsupported.

Backend selection only creates the corresponding storage implementation. It
does not convert existing logs, metadata, or snapshots. Changing configuration
is not a data migration procedure for existing nodes. A complete storage copy
retains its original storeId.

## 3. Storage Layout And Identity

`JRaftServer.createMultiRaftGroup()` and
[JRaftUtils.initDirectory()](../../../core/src/main/java/com/alibaba/nacos/core/distributed/raft/utils/JRaftUtils.java)
assign separate directories to each group. `FileLogStorage` creates `file-log`
under the group's log URI:

```text
<nacos.home>/data/protocol/raft/<group>/
├── log/
│   └── file-log/
│       ├── LOCK
│       ├── manifest
│       ├── manifest.tmp
│       ├── segments/
│       │   └── 00000000000000000001.seg
│       └── index/
│           └── 00000000000000000001.idx
├── meta-data/
└── snapshot/
```

`LOCK` provides an exclusive lock through `FileChannel.tryLock()`. Creating the
first Manifest generates a UUID `storeId`, which subsequent segment headers share.
Recovery validates the Manifest groupId and each segment's storeId and ID. The
storeId persists with the data and survives copying the entire store.

Segment and index filenames use ASCII decimal IDs padded to 20 digits, with
`.seg` and `.idx` extensions. Before cleanup, initialization scans canonical
segment filenames and combines them with the largest Manifest ID to calculate
the next ID. Preserved files of unknown ownership also participate. Overflow
fails initialization; noncanonical filenames do not participate in ID recognition.

## 4. Binary Formats

Multibyte values use big-endian encoding; the format version is `1`. UUIDs encode
the high 64 bits followed by the low 64 bits. CRC32 uses `java.util.zip.CRC32`;
CRC64 uses JRaft's `CrcUtil.crc64()`. Ranges below are half-open; offsets and
lengths are in bytes.

### 4.1 Segment Header

Production segments have a 128 MiB limit, including the 40-byte header and records.

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 4 | magic: `0x4e4c5331` (`NLS1`) |
| 4 | 2 | version |
| 6 | 2 | headerSize: 40 |
| 8 | 16 | storeId |
| 24 | 8 | segmentId, greater than 0 |
| 32 | 8 | CRC64 over `[0, 32)` |

### 4.2 Log Records

The factory's JRaft codec encodes a LogEntry into a payload, which is wrapped as
a file record. A payload of length `L` produces a record of length `24 + L`,
with a total record limit of 64 MiB.

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 4 | magic: `0x4e4c5231` (`NLR1`) |
| 4 | 2 | version |
| 6 | 2 | headerSize: 16 |
| 8 | 4 | payloadLength: L |
| 12 | 4 | CRC32 over `[0, 12)` |
| 16 | L | JRaft LogEntry payload |
| 16 + L | 8 | payload CRC64 |

Reads validate the header, length, and record boundaries before allocating and
reading the payload. Encoding and decoding validate positive indexes,
nonnegative terms, and valid entry types. Configuration entries also validate
membership relationships among peers, learners, and the old configuration.
Recovery scanning requires decoded indexes to match the expected continuous sequence.

### 4.3 Manifest

The Manifest stores the storeId, UTF-8 groupId, locally valid log range, and
ordered segment descriptors. With a groupId of `G` UTF-8 bytes and `N` segments,
its total length is `60 + G + 40N`. The file limit is 16 MiB. The groupId must
be nonblank and fit within 1024 UTF-8 bytes. Invalid UTF-8 and strings that
cannot be encoded correctly are rejected.

| Offset | Length | Field |
| --- | --- | --- |
| 0 | 4 | magic: `0x4e4c4d31` (`NLM1`) |
| 4 | 2 | version |
| 6 | 2 | headerSize: 52 |
| 8 | 4 | total file length |
| 12 | 16 | storeId |
| 28 | 8 | firstLogIndex |
| 36 | 8 | lastLogIndex |
| 44 | 4 | groupId byte length G |
| 48 | 4 | segment count N |
| 52 | G | UTF-8 groupId |
| 52 + G | 40N | segment descriptors |
| 52 + G + 40N | 8 | CRC64 over all preceding bytes |

Each descriptor contains five longs in order: `segmentId`, `firstIndex`,
`lastIndex`, `firstRecordOffset`, and `publishedEndOffset`. Segment IDs strictly
increase and log ranges are continuous. Each segment's valid byte range is
`[firstRecordOffset, publishedEndOffset)`. Global first and last indexes must
match descriptor ranges.

The Manifest describes local retention. Raft majority commit and state-machine
apply are handled by the protocol above it.

### 4.4 Index

Indexes have no header. Each 12-byte slot contains a long record offset followed
by an int total record length. Its byte offset is
`(logIndex - baseIndex) × 12`; file length follows the number of retained entries
in the segment. Reads validate the slot, record length, and containment within
the Manifest's published byte range.

Indexes are rebuilt by scanning segments. Initialization rebuilds all published
segment indexes. Appends update indexes synchronously, but publication ordering
does not depend on forcing index files.

## 5. Appends And Range Changes

`FileLogStorage` serializes mutations with a read/write lock. `getEntry()` locates
the segment, reads the index, validates the record, and decodes it under the read
lock. Appends, truncations, reset, and shutdown execute under the write lock.

### 5.1 Batch Append

`appendEntries()` first encodes and validates the whole batch: indexes must be
continuous from `lastLogIndex + 1`, each record must fit in an empty segment, and
the planned Manifest must satisfy format and size limits. Validation failure
returns 0 before writing batch files.

Write and publication ordering:

1. Append records to the active segment and update its index. On insufficient
   space, force the old segment, close it, and create another.
2. Force the final segment after writing the whole batch.
3. Synchronize the `segments` directory if the new Manifest references new segments.
4. Write `manifest.tmp` and call `force(true)`.
5. Replace `manifest` with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
6. Synchronize the `file-log` directory, update the in-memory Manifest, and return
   the full batch size.

Directory synchronization follows the platform policy and durability boundaries
in section 7.

### 5.2 Prefix/Suffix Truncation And Reset

| Operation | Published range | Resource handling after publication |
| --- | --- | --- |
| `truncatePrefix(firstIndexKept)` | Retains entries from the target onward; a target beyond the old end publishes an empty range starting there. | Rebuild the boundary index, reopen active resources, and reclaim obsolete segments. |
| `truncateSuffix(lastIndexKept)` | Retains entries through the target; a target before the old first index publishes an empty range starting at the old first index. | Truncate and force the boundary segment, truncate its index, reopen active resources, and reclaim obsolete segments. |
| `reset(nextLogIndex)` | Publishes the empty range `[nextLogIndex, nextLogIndex - 1]`. | Close and rebuild empty writer state, and reclaim old segments. |

If a truncation boundary is within the retained range, read and validate its
record before publishing the new Manifest. All these operations publish the
range before handling physical resources. Reset creates no placeholder entry;
removed entries and stale configuration logs must no longer be readable.
JRaft's snapshot/LogManager path supplies snapshot indexes, terms, and membership.
Continuous appends must start from `nextLogIndex`, both immediately and after
restart. Reads in an empty range return null; `getTerm()` returns 0.

Log-index, ID, and offset arithmetic checks for overflow. `reset(Long.MAX_VALUE)`
can represent an empty range, but an append there must fail because the next
index would overflow.

## 6. Initialization And Recovery

`init()` performs these steps:

1. Check synchronous-write options, groupId, ConfigurationManager, and the LogEntry codec.
2. Create the root directory, acquire its exclusive lock, and create `segments`
   and `index`. Synchronize both subdirectories and the root's ancestor chain
   through the filesystem root.
3. Read and validate the Manifest. Without one, only empty subdirectories, the
   lock file, and a regular temporary Manifest are allowed; then create empty storage.
4. Calculate the segment ID high-water mark before cleanup.
5. Scan all published records, validate storage identity and continuity, rebuild
   indexes, and collect configuration entries.
6. Check unreferenced segments and classify reclaimable and preserved files.
7. Synchronize the validated Manifest's directory to establish the recovery barrier.
8. Truncate and force unpublished tails of published segments; reclaim obsolete
   segments, unused indexes, and the temporary Manifest.
9. Reopen active resources, add collected configuration entries to
   ConfigurationManager, and return success only with complete resources.

Existing directories follow the same ancestor synchronization call order.
Windows validates directories without establishing an actual directory durability
barrier; other platforms synchronize them. Before the recovery barrier call,
the Manifest and segments remain unchanged; derived indexes may be rebuilt.
Missing published data, checksum failures, discontinuous ranges, and identity
mismatches fail initialization. Platform durability boundaries are in section 7.

### 6.1 Unreferenced Segment Classification

The following applies to canonical filenames in `segments` that the Manifest
does not reference.

| Check result | Behavior |
| --- | --- |
| Valid header with matching storeId and filename ID | Reclaimable after the barrier. |
| Regular file with incomplete header, damaged magic/header size, or failed header checksum | Preserve in place, log WARN with the path, and continue recovery of published logs. |
| Header identifies another storeId, disagrees with the filename ID, has an unsupported version, or has an invalid identity | Fail initialization. |
| Non-regular file, symbolic link, or actual I/O error | Fail initialization. |

Files of unknown ownership are never replayed. Their canonical filenames
continue to participate in ID allocation on subsequent initialization.

## 7. Filesystem And Platform Constraints

| Operation | Code path | Failure handling |
| --- | --- | --- |
| Exclusive lock | `acquireDirectoryLock()` calls `tryLock()` on `LOCK`. | Fail initialization and release acquired resources. |
| File synchronization | `Segment.force()` and `FileChannel.force(true)` on the temporary Manifest. | Fail initialization or mutation. |
| Directory synchronization | `forceDirectory()` validates a real directory with `NOFOLLOW_LINKS`; Windows then skips synchronization. Other platforms open with `READ`, `NOFOLLOW_LINKS` and call `force(true)`. | Path validation failures propagate. Outside Windows, synchronization failures in initialization, publication, and the recovery barrier propagate; failures after reclamation log WARN. |
| Atomic Manifest replacement | `writeManifest()` moves a same-directory temporary file with `ATOMIC_MOVE`, `REPLACE_EXISTING`. | Fail publication; validate existing disk changes on the next recovery. |
| File type checks | Segment, Manifest, and directory checks use `NOFOLLOW_LINKS`. | Refuse to open paths that do not satisfy the required regular-file or real-directory type. |

These operations act directly on the filesystem containing the configured path.
Initialization calls synchronization for `segments`, `index`, `file-log`, and all
ancestor directories in the same order, even if directories already exist.
JRaft's `Platform.isWindows()` identifies Windows, which automatically skips
directory synchronization after validation without adding configuration. Other
platforms retain strict synchronization. Regular-file forcing, exclusive locks,
and atomic same-directory Manifest replacement remain mandatory.

Each successful actual initialization on Windows logs one WARN with the storage
path and the power-loss durability limitation caused by skipped directory
synchronization. Idempotent initialization and appends do not repeat it. Windows
cannot guarantee recovery from power loss where directory synchronization
barriers are required: acknowledged ranges may be lost, or recovery may fail
when the Manifest disagrees with truncation or reclamation results. Process
termination tests verify process recovery and cannot prove power-loss safety.

## 8. Failure States And Diagnostics

Parameter, encoding, or batch-planning failures return before file writes and
leave storage usable. Required I/O or resource-reopen failures during a mutation
close active segments/indexes and clear writer state. Further appends,
truncations, and resets are rejected. The directory lock remains held until
`shutdown()`; a subsequent `init()` recovers from disk after shutdown.

Directory synchronization or later resource handling can fail after the Manifest
has moved. A failed return can therefore coexist with a new Manifest on disk;
recovery follows the validated disk state. Deletion failures and failures to
synchronize the segment directory after reclamation log WARN and leave the
published range valid.

### 8.1 Runtime States And Return Values

| State or operation | Observable behavior |
| --- | --- |
| Successful initialization | `init()` returns true, holds the directory lock, and has complete read/write resources. |
| Failed initialization | `init()` cleans up resources and returns false; exception paths log ERROR with the root path and exception. |
| Unopened or closed | First/last indexes return 0, reads return null, appends return 0, and truncations/reset return false. |
| Out-of-range read | `getEntry()` returns null; `getTerm()` returns 0. |
| Read I/O or decoding failure | Log ERROR with directory, index, and exception; return null. |
| Batch prevalidation failure | Log WARN and return 0 before writing the batch. |
| Required mutation step failure | Log ERROR, close active resources, and reject further mutations; retain the directory lock. |
| Obsolete file reclamation failure | Log WARN; the published range remains valid. |
| Shutdown | `shutdown()` closes active resources, releases the lock, and clears runtime state. |

Recovery after mutation failure requires `shutdown()` followed by `init()` on the
original directory. Repeated `init()` while holding the lock only checks current
runtime state and does not rerun recovery.

### 8.2 Recovery Diagnostics

`FileLogStorage` initialization and recovery checks produce these messages:

| Message or check | Meaning |
| --- | --- |
| `File log directory is already in use` | The exclusive lock could not be acquired; lock-file existence and lock ownership are separate facts. |
| `Manifest is missing with existing data` | No Manifest exists, but `segments` or `index` contains data. |
| `Manifest group does not match JRaft group` | The Manifest groupId differs from the initialization context. |
| `Preserving unpublished segment with unknown ownership` | An unreferenced regular file has an incomplete or damaged header; preserve it and continue recovery. |
| `Foreign segment in file log directory` | An unreferenced segment's valid header belongs to another storeId; fail initialization. |
| `Segment id does not match its file name` | An unreferenced segment's header ID differs from its canonical filename; fail initialization. |
| `Segment identity does not match manifest` | A published segment's storeId or ID differs from the Manifest. |
| `Segment ID exhausted` / `File ID overflow` | No further ID can be allocated, or a filename ID exceeds the long range. |

## 9. Verification Requirements

Changes to the file log backend must include focused tests for:

- default backend selection, case/whitespace handling, invalid configuration,
  synchronous-write constraints, and factory selection for every group;
- default metadata, snapshot, and codec delegation, plus property binding, group
  assembly, writes, restart recovery, and continued commits without RocksDB classes;
- continuous appends, term reads, segment rolling, whole-batch validation failure,
  and absence of partial publication from a rejected batch;
- prefix/suffix truncation within and across segments, empty reset, stale
  configuration removal, continuous appends after reopening, and log-index,
  segment-ID, and offset overflow boundaries;
- header, CRC, length, UTF-8, and identity validation, bounds checks before payload
  allocation, derived index rebuilding, short I/O, EOF, and zero progress;
- concurrent reads during append, truncation, and shutdown, observing only complete
  entries or the corresponding out-of-range or closed state;
- ancestor synchronization ordering for new and existing directories, exclusive
  directory locking, and no early publication or writable state on required
  synchronization failure;
- published-data corruption, unreferenced-file classification, preservation of
  unknown ownership, ID high-water calculation, and unchanged Manifest/segment
  bytes before the recovery barrier;
- process interruption at segment creation, partial/complete headers, Manifest
  replacement, tail truncation, and reclamation, followed by repeated recovery
  and continued writes;
- disabled mutations after required file I/O, directory synchronization, and
  resource-reopen failures, no rollback of published ranges, and best-effort reclamation;
- failure propagation through LogManager, reset/snapshot boundaries, and retained
  terms and configuration history;
- real RPC replication, snapshot installation on lagging nodes, conflicting
  suffix replacement, immediate and post-restart replication after reset,
  membership changes, catch-up after killing a leader/follower, and continued
  commits after restarting all nodes;
- storage lifecycle and process recovery on Linux and Windows JDK 17. Local
  platform mocks must verify that Windows validates paths without opening
  directory channels, other platforms retain directory forcing and error
  propagation, and regular-file forcing failures cannot be bypassed on either.

Integration verification must distinguish real files, real RPC, and independent
node processes; mocks cannot replace the corresponding recovery assertions.
Process control and diagnostics must share timeout budgets, preserve failure
evidence, and continue cleaning other nodes when one node's cleanup fails.
Local platform mocks do not replace verification on the actual platform.
Calling the directory synchronization entry point does not establish that a
real force occurred. Process termination does not simulate an operating-system
crash or power loss and cannot prove power-loss safety.

## 10. Related Specs

- [CP Consistency Spec](foundation-cp-consistency-spec.md)
- [Persistence And Dump Spec](foundation-persistence-dump-spec.md)
- [Server Lifecycle And Environment Configuration Spec](foundation-server-lifecycle-env-spec.md)
