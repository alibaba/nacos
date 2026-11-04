/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.distributed.raft.storage.file;

import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Process death at actual publication and reclamation I/O, with platform-specific directory sync.
 * Process termination does not simulate power loss or establish directory durability on Windows.
 * Failed tests retain the temporary directory containing storage files and phase logs.
 */
public class FileLogProcessRecoveryTest {
    
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;
    
    @ParameterizedTest
    @ValueSource(strings = {"suffix-tail", "suffix-orphan", "reset-orphan"})
    void recoversAfterPublicationAndReclamationProcessesAreKilled(String scenario)
        throws Exception {
        createFixture(directory);
        killAtCheckpoint("publish", scenario, "MANIFEST_MOVED");
        Manifest published = readManifest(directory);
        assertPublishedRange(published, scenario);
        killAtCheckpoint("recover", scenario,
            scenario.endsWith("tail") ? "TAIL_TRUNCATED" : "ORPHAN_DELETED");
        assertEquals(published, readManifest(directory));
        runChild("verify", scenario);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"created", "partial", "header"})
    void recoversAfterSegmentCreationProcessIsKilled(String scenario) throws Exception {
        createFixture(directory);
        Manifest before = readManifest(directory);
        killAtCheckpoint("create", scenario, "SEGMENT_" + scenario);
        Path orphan = segmentPath(directory, 3);
        byte[] residual = Files.readAllBytes(orphan);
        assertEquals("created".equals(scenario) ? 0 : "partial".equals(scenario) ? 8 : 40,
            residual.length);
        for (int attempt = 0; attempt < 2; attempt++) {
            FileLogStorage recovered = store(directory);
            try {
                assertTrue(recovered.init(context()));
                assertEquals(before, recovered.getManifest());
                for (long index = 1; index <= 6; index++) {
                    assertEquals(entry(index), recovered.getEntry(index));
                }
                if ("header".equals(scenario)) {
                    assertFalse(Files.exists(orphan));
                } else {
                    assertArrayEquals(residual, Files.readAllBytes(orphan));
                }
            } finally {
                recovered.shutdown();
            }
        }
        FileLogStorage recovered = store(directory);
        try {
            assertTrue(recovered.init(context()));
            assertTrue(recovered.appendEntry(entry(7)));
            long allocated = recovered.getManifest().getSegments()
                .get(recovered.getManifest().getSegments().size() - 1).getSegmentId();
            assertTrue(allocated > ("header".equals(scenario) ? 2 : 3));
        } finally {
            recovered.shutdown();
        }
        FileLogStorage reopened = store(directory);
        try {
            assertTrue(reopened.init(context()));
            assertEquals(entry(7), reopened.getEntry(7));
            assertTrue(reopened.appendEntry(entry(8)));
        } finally {
            reopened.shutdown();
        }
    }
    
    @Test
    void failedRecoveryBarrierPreservesManifestAndEverySegment() throws Exception {
        createFixture(directory);
        killAtCheckpoint("publish", "suffix-tail", "MANIFEST_MOVED");
        byte[] manifest = Files.readAllBytes(root(directory).resolve("manifest"));
        Map<Path, byte[]> segments = segmentBytes(directory);
        runChild("fail-force", "suffix-tail");
        assertArrayEquals(manifest, Files.readAllBytes(root(directory).resolve("manifest")));
        assertEquals(segments.keySet(), segmentBytes(directory).keySet());
        for (Map.Entry<Path, byte[]> segment : segments.entrySet()) {
            assertArrayEquals(segment.getValue(), Files.readAllBytes(segment.getKey()));
        }
        runChild("verify", "suffix-tail");
    }
    
    private void killAtCheckpoint(String phase, String scenario, String expected) throws Exception {
        Path output = directory.resolve(phase + "-" + scenario + ".log");
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(30000);
            Process child = startChild(phase, scenario, server.getLocalPort(), output);
            try {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(30000);
                    assertEquals(expected, new DataInputStream(socket.getInputStream()).readUTF(),
                        Files.readString(output));
                    child.destroyForcibly();
                    assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Child still owns file lock");
                    assertFalse(child.isAlive());
                }
            } finally {
                stopChild(child);
            }
        }
    }
    
    private void runChild(String phase, String scenario) throws Exception {
        Path output = directory.resolve(phase + "-" + scenario + ".log");
        Process child = startChild(phase, scenario, 0, output);
        try {
            assertTrue(child.waitFor(60, TimeUnit.SECONDS), "Child timed out: " + output);
            String log = Files.readString(output);
            assertEquals(0, child.exitValue(), log);
            assertTrue(log.contains("FILE_LOG_PROCESS_OK"), log);
        } finally {
            stopChild(child);
        }
    }
    
    private Process startChild(String phase, String scenario, int port, Path output)
        throws IOException {
        String classpath = System.getProperty("surefire.test.class.path",
            System.getProperty("java.class.path"));
        Path arguments = directory.resolve(phase + "-" + scenario + ".args");
        Files.writeString(arguments, "-cp\n\"" + classpath.replace('\\', '/') + "\"\n"
            + FileLogProcessRecoveryTest.class.getName() + "\n\""
            + directory.toString().replace('\\', '/') + "\"\n" + phase + "\n" + scenario
            + "\n" + port + "\n", StandardCharsets.UTF_8);
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
            .toString(), "@" + arguments).redirectErrorStream(true)
            .redirectOutput(output.toFile()).start();
    }
    
    private static void stopChild(Process child) throws InterruptedException {
        if (child.isAlive()) {
            child.destroyForcibly();
            assertTrue(child.waitFor(10, TimeUnit.SECONDS));
        }
    }
    
    /**
     * Executes a single isolated crash or recovery phase.
     *
     * @param args store directory, phase, scenario and checkpoint TCP port
     * @throws Exception if real I/O, assertions or checkpoint communication fails
     */
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        String phase = args[1];
        String scenario = args[2];
        int port = Integer.parseInt(args[3]);
        switch (phase) {
            case "create":
                crashSegmentCreation(directory, scenario, port);
                break;
            case "publish":
                crashPublication(directory, scenario, port);
                break;
            case "recover":
            case "fail-force":
                recoverWithInterception(directory, scenario, port, "fail-force".equals(phase));
                break;
            case "verify":
                verifyRecoveredStore(directory, scenario);
                break;
            default:
                throw new IllegalArgumentException("Unknown process phase: " + phase);
        }
        System.out.println("FILE_LOG_PROCESS_OK");
    }
    
    private static void crashSegmentCreation(Path directory, String scenario, int port)
        throws Exception {
        FileLogStorage store = store(directory);
        assertTrue(store.init(context()));
        Path target = segmentPath(directory, 3);
        // Intercept only the new segment. Every open/write/fsync uses the real filesystem.
        try (MockedStatic<FileChannel> channels = mockStatic(FileChannel.class, invocation -> {
            FileChannel channel = (FileChannel) invocation.callRealMethod();
            if (!"open".equals(invocation.getMethod().getName())
                || !target.equals(invocation.getArgument(0))) {
                return channel;
            }
            if ("created".equals(scenario)) {
                checkpoint(port, "SEGMENT_" + scenario);
            }
            FileChannel observed = spy(channel);
            doAnswer(write -> {
                ByteBuffer buffer = write.getArgument(0);
                long offset = write.getArgument(1);
                if ("partial".equals(scenario)) {
                    ByteBuffer prefix = buffer.duplicate();
                    prefix.limit(prefix.position() + 8);
                    while (prefix.hasRemaining()) {
                        offset += channel.write(prefix, offset);
                    }
                } else {
                    write.callRealMethod();
                }
                checkpoint(port, "SEGMENT_" + scenario);
                throw new AssertionError("Crash checkpoint returned");
            }).when(observed).write(any(ByteBuffer.class), anyLong());
            return observed;
        })) {
            store.appendEntry(entry(7));
            throw new AssertionError("Segment creation checkpoint was not reached");
        } finally {
            store.shutdown();
        }
    }
    
    private static void crashPublication(Path directory, String scenario, int port)
        throws Exception {
        FileLogStorage store = store(directory);
        assertTrue(store.init(context()));
        Path root = root(directory);
        Map<Path, byte[]> oldSegments = segmentBytes(directory);
        AtomicBoolean moved = new AtomicBoolean();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("move".equals(invocation.getMethod().getName())
                && root.resolve("manifest").equals(invocation.getArgument(1))) {
                assertPublishedRange(readManifest(directory), scenario);
                moved.set(true);
            }
            return result;
        }); MockedStatic<FileLogStorage> stores = mockStatic(FileLogStorage.class, invocation -> {
            if ("forceDirectory".equals(invocation.getMethod().getName())
                && root.equals(invocation.getArgument(0)) && moved.get()) {
                for (Map.Entry<Path, byte[]> segment : oldSegments.entrySet()) {
                    assertArrayEquals(segment.getValue(), Files.readAllBytes(segment.getKey()));
                }
                checkpoint(port, "MANIFEST_MOVED");
            }
            return invocation.callRealMethod();
        })) {
            if (scenario.startsWith("reset")) {
                assertTrue(store.reset(20));
            } else {
                assertTrue(store.truncateSuffix(2));
            }
            throw new AssertionError("Publication checkpoint was not reached");
        } finally {
            store.shutdown();
        }
    }
    
    private static void recoverWithInterception(Path directory, String scenario, int port,
        boolean failForce) throws Exception {
        Path root = root(directory);
        Manifest published = readManifest(directory);
        Set<Path> orphans = new HashSet<>(segmentBytes(directory).keySet());
        for (Manifest.SegmentMeta segment : published.getSegments()) {
            orphans.remove(segmentPath(directory, segment.getSegmentId()));
        }
        Set<Path> validatedOrphans = new HashSet<>();
        AtomicInteger rootForces = new AtomicInteger();
        AtomicBoolean barrierPassed = new AtomicBoolean();
        FileLogStorage store = store(directory);
        try (MockedStatic<Segment> segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment opened = (Segment) result;
                Path path = invocation.getArgument(0);
                if (orphans.contains(path)) {
                    assertEquals(published.getStoreId(), opened.getHeader().getStoreId());
                    validatedOrphans.add(path);
                }
                Segment intercepted = spy(opened);
                doAnswer(truncate -> {
                    assertTrue(barrierPassed.get(), "Tail reclamation preceded recovery fsync");
                    Object value = truncate.callRealMethod();
                    if (scenario.endsWith("tail")) {
                        checkpoint(port, "TAIL_TRUNCATED");
                    }
                    return value;
                }).when(intercepted).truncateToLogicalEnd();
                return intercepted;
            }
            return result;
        }); MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if ("deleteIfExists".equals(invocation.getMethod().getName())
                && orphans.contains(invocation.getArgument(0))) {
                assertTrue(barrierPassed.get(), "Orphan reclamation preceded recovery fsync");
                Object result = invocation.callRealMethod();
                assertEquals(Boolean.TRUE, result);
                if (scenario.endsWith("orphan")) {
                    checkpoint(port, "ORPHAN_DELETED");
                }
                return result;
            }
            return invocation.callRealMethod();
        }); MockedStatic<FileLogStorage> stores = mockStatic(FileLogStorage.class, invocation -> {
            if ("forceDirectory".equals(invocation.getMethod().getName())
                && root.equals(invocation.getArgument(0)) && rootForces.incrementAndGet() == 2) {
                assertEquals(orphans, validatedOrphans, "Orphan ownership was not fully validated");
                for (Manifest.SegmentMeta segment : published.getSegments()) {
                    Path indexPath = root.resolve("index")
                        .resolve(FileLogFormat.indexName(segment.getSegmentId()));
                    assertEquals((segment.getLastIndex() - segment.getFirstIndex() + 1)
                        * FileLogFormat.INDEX_ENTRY_SIZE, Files.size(indexPath),
                        "Published records were not fully validated before the barrier");
                }
                if (failForce) {
                    throw new IOException("Injected recovery root fsync failure");
                }
                Object result = invocation.callRealMethod();
                barrierPassed.set(true);
                return result;
            }
            return invocation.callRealMethod();
        })) {
            if (failForce) {
                assertFalse(store.init(context()));
                assertEquals(2, rootForces.get(), "Recovery barrier was not attempted");
                assertFalse(barrierPassed.get());
            } else {
                store.init(context());
                throw new AssertionError("Reclamation checkpoint was not reached");
            }
        } finally {
            store.shutdown();
        }
    }
    
    private static void verifyRecoveredStore(Path directory, String scenario) throws Exception {
        FileLogStorage store = store(directory);
        try {
            assertTrue(store.init(context()), "Killed process did not release the directory lock");
            Manifest published = store.getManifest();
            assertPublishedRange(published, scenario);
            assertEquals(published.getFirstLogIndex(), store.getFirstLogIndex());
            assertEquals(published.getLastLogIndex(), store.getLastLogIndex());
            for (long index = published.getFirstLogIndex(); index <= published
                .getLastLogIndex(); index++) {
                assertEquals(entry(index), store.getEntry(index));
            }
            for (long index = 1; index <= 20; index++) {
                if (index < published.getFirstLogIndex() || index > published.getLastLogIndex()) {
                    assertNull(store.getEntry(index), "Old log entry survived at " + index);
                }
            }
            Set<Path> retained = new HashSet<>();
            for (Manifest.SegmentMeta segment : published.getSegments()) {
                Path path = segmentPath(directory, segment.getSegmentId());
                retained.add(path);
                assertEquals(segment.getPublishedEndOffset(), Files.size(path),
                    "Unpublished tail survived recovery");
                Path indexPath = root(directory).resolve("index")
                    .resolve(FileLogFormat.indexName(segment.getSegmentId()));
                try (Index index = Index.open(indexPath, segment.getFirstIndex(),
                    segment.getLastIndex())) {
                    for (long logIndex = segment.getFirstIndex(); logIndex <= segment
                        .getLastIndex(); logIndex++) {
                        assertNotNull(index.read(logIndex, segment.getFirstRecordOffset(),
                            segment.getPublishedEndOffset()));
                    }
                }
            }
            assertEquals(retained, segmentBytes(directory).keySet(),
                "Old segments survived recovery");
            Set<String> expectedIndexes = new HashSet<>();
            for (Manifest.SegmentMeta segment : published.getSegments()) {
                expectedIndexes.add(FileLogFormat.indexName(segment.getSegmentId()));
            }
            Set<String> actualIndexes = new HashSet<>();
            try (DirectoryStream<Path> indexes = Files.newDirectoryStream(root(directory)
                .resolve("index"))) {
                for (Path index : indexes) {
                    actualIndexes.add(index.getFileName().toString());
                }
            }
            assertEquals(expectedIndexes, actualIndexes, "Unused indexes survived");
            long appendedIndex = published.getLastLogIndex() + 1;
            assertTrue(store.appendEntry(entry(appendedIndex)));
            assertEquals(entry(appendedIndex), store.getEntry(appendedIndex));
            store.shutdown();
            FileLogStorage reopened = store(directory);
            try {
                assertTrue(reopened.init(context()), "Directory lock could not be reacquired");
                assertEquals(published.getFirstLogIndex(), reopened.getFirstLogIndex());
                assertEquals(appendedIndex, reopened.getLastLogIndex());
                for (long index = published.getFirstLogIndex(); index <= appendedIndex; index++) {
                    assertEquals(entry(index), reopened.getEntry(index));
                }
            } finally {
                reopened.shutdown();
            }
        } finally {
            store.shutdown();
        }
    }
    
    private static void checkpoint(int port, String phase) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF(phase);
            output.flush();
            // The parent's explicit kill ends this phase; elapsed time is no signal.
            new CountDownLatch(1).await();
        }
    }
    
    private static void createFixture(Path directory) throws Exception {
        FileLogStorage store = store(directory);
        try {
            assertTrue(store.init(context()));
            List<LogEntry> entries = new ArrayList<>();
            for (long index = 1; index <= 6; index++) {
                entries.add(entry(index));
            }
            assertEquals(6, store.appendEntries(entries));
            assertEquals(2, store.getManifest().getSegments().size());
        } finally {
            store.shutdown();
        }
    }
    
    private static void assertPublishedRange(Manifest manifest, String scenario) {
        assertEquals(scenario.startsWith("reset") ? 20 : 1, manifest.getFirstLogIndex());
        assertEquals(scenario.startsWith("reset") ? 19 : 2, manifest.getLastLogIndex());
    }
    
    private static Manifest readManifest(Path directory) throws IOException {
        return store(directory).decodeManifest(
            ByteBuffer.wrap(Files.readAllBytes(root(directory).resolve("manifest"))));
    }
    
    private static Map<Path, byte[]> segmentBytes(Path directory) throws IOException {
        Map<Path, byte[]> result = new HashMap<>();
        try (DirectoryStream<Path> segments = Files.newDirectoryStream(root(directory)
            .resolve("segments"), "*.seg")) {
            for (Path segment : segments) {
                result.put(segment, Files.readAllBytes(segment));
            }
        }
        return result;
    }
    
    private static FileLogStorage store(Path directory) {
        LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
        try {
            long capacity = FileLogFormat.SEGMENT_HEADER_SIZE
                + 3L * codec.encode(entry(1)).remaining();
            return new FileLogStorage(directory, new RaftOptions(), capacity);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private static Path root(Path directory) {
        return directory.toAbsolutePath().normalize().resolve("file-log");
    }
    
    private static Path segmentPath(Path directory, long id) {
        return root(directory).resolve("segments").resolve(FileLogFormat.segmentName(id));
    }
    
    private static LogEntry entry(long index) {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 7));
        entry.setData(ByteBuffer.wrap(new byte[] {(byte) index, 42}));
        return entry;
    }
    
    private static LogStorageOptions context() {
        LogStorageOptions options = new LogStorageOptions();
        options.setGroupId("process-recovery");
        options.setConfigurationManager(new ConfigurationManager());
        options.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        return options;
    }
}
