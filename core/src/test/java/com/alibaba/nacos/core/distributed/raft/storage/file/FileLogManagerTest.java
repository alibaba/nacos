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

import com.alipay.sofa.jraft.FSMCaller;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.impl.RocksDBLogStorage;
import com.alipay.sofa.jraft.core.NodeMetrics;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.EnumOutter.ErrorType;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.RaftOutter.SnapshotMeta;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogManagerOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogManager;
import com.alipay.sofa.jraft.storage.impl.LogManagerImpl;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class FileLogManagerTest {
    
    @TempDir
    Path directory;
    
    @ParameterizedTest
    @ValueSource(strings = {"append", "truncateSuffix", "reset"})
    void storagePublicationFailurePoisonsLogManager(String operation) throws Exception {
        FileLogStorage.forceDirectory(directory);
        LogStorage storage = spy(storage(true));
        LogManagerImpl manager = new LogManagerImpl();
        FSMCaller caller = mock(FSMCaller.class);
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<RaftException> error = new AtomicReference<>();
        AtomicReference<Object> storageResult = new AtomicReference<>();
        doAnswer(invocation -> {
            error.set(invocation.getArgument(0));
            reported.countDown();
            return true;
        }).when(caller).onError(any(RaftException.class));
        try {
            assertTrue(manager.init(managerOptions(storage, new ConfigurationManager(), caller)));
            assertTrue(append(manager, entries(1, 3, 1)).isOk());
            Path root = directory.resolve("file-log");
            byte[] manifest = Files.readAllBytes(root.resolve("manifest"));
            // Fail the real manifest open on the disk worker, without thread-local mocks.
            Files.createDirectory(root.resolve("manifest.tmp"));
            switch (operation) {
                case "append":
                    doAnswer(invocation -> {
                        Object result = invocation.callRealMethod();
                        storageResult.set(result);
                        return result;
                    }).when(storage).appendEntries(anyList());
                    assertEquals(RaftError.EIO.getNumber(),
                        append(manager, entries(4, 4, 1)).getCode());
                    break;
                case "truncateSuffix":
                    doAnswer(invocation -> {
                        Object result = invocation.callRealMethod();
                        storageResult.set(result);
                        return result;
                    }).when(storage).truncateSuffix(1);
                    assertEquals(RaftError.EIO.getNumber(),
                        append(manager, entries(2, 3, 2)).getCode());
                    break;
                case "reset":
                    doAnswer(invocation -> {
                        Object result = invocation.callRealMethod();
                        storageResult.set(result);
                        return result;
                    }).when(storage).reset(3);
                    manager.setSnapshot(snapshot(2, 2));
                    break;
                default:
                    throw new AssertionError("Unknown operation: " + operation);
            }
            assertTrue(reported.await(10, TimeUnit.SECONDS));
            assertEquals("append".equals(operation) ? Integer.valueOf(0) : Boolean.FALSE,
                storageResult.get());
            assertEquals(ErrorType.ERROR_TYPE_LOG, error.get().getType());
            assertEquals(RaftError.EIO.getNumber(), error.get().getStatus().getCode());
            assertArrayEquals(manifest, Files.readAllBytes(root.resolve("manifest")));
            Map<Path, byte[]> failedState = diskFiles(root);
            clearInvocations(storage);
            assertEquals(RaftError.EIO.getNumber(),
                append(manager, entries(4, 4, 2)).getCode());
            verifyNoInteractions(storage);
            Map<Path, byte[]> afterRejectedAppend = diskFiles(root);
            assertEquals(failedState.keySet(), afterRejectedAppend.keySet());
            failedState.forEach((path, bytes) -> assertArrayEquals(bytes,
                afterRejectedAppend.get(path), path.toString()));
        } finally {
            manager.shutdown();
            manager.join();
            storage.shutdown();
        }
    }
    
    private static Map<Path, byte[]> diskFiles(Path root) throws IOException {
        Map<Path, byte[]> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path), Files.readAllBytes(path));
            }
        }
        return result;
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetConfigurationBoundaryRespectsBackendContract(boolean fileBackend) throws Exception {
        if (fileBackend) {
            FileLogStorage.forceDirectory(directory);
        }
        ConfigurationManager configurations = new ConfigurationManager();
        LogStorage storage = spy(storage(fileBackend));
        CountDownLatch resetCompleted = new CountDownLatch(1);
        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } finally {
                resetCompleted.countDown();
            }
        }).when(storage).reset(3);
        LogManagerImpl manager = new LogManagerImpl();
        FSMCaller caller = mock(FSMCaller.class);
        LogEntry configuration = new LogEntry(EntryType.ENTRY_TYPE_CONFIGURATION);
        configuration.setId(new LogId(3, 1));
        configuration.setPeers(List.of(new PeerId("127.0.0.1", 8082)));
        configuration.setOldPeers(List.of(new PeerId("127.0.0.1", 8083)));
        configuration.setLearners(List.of(new PeerId("127.0.0.1", 8084)));
        configuration.setOldLearners(List.of(new PeerId("127.0.0.1", 8085)));
        SnapshotMeta snapshot = snapshot(2, 2);
        try {
            assertTrue(manager.init(managerOptions(storage, configurations, caller)));
            List<LogEntry> batch = entries(1, 2, 1);
            batch.add(configuration);
            assertTrue(append(manager, batch).isOk());
            assertEquals(new LogId(3, 1), configurations.getLastConfiguration().getId());
            // A conflicting snapshot term schedules real LogManager reset(3).
            manager.setSnapshot(snapshot);
            assertTrue(resetCompleted.await(10, TimeUnit.SECONDS));
            assertEquals(new LogId(2, 2), configurations.getLastConfiguration().getId());
            assertEquals(List.of(new PeerId("127.0.0.1", 8081)),
                configurations.getLastConfiguration().getConf().listPeers());
            assertSnapshotConfiguration(configurations);
            assertEquals(3, storage.getFirstLogIndex());
            if (fileBackend) {
                assertEquals(2, storage.getLastLogIndex());
                assertNull(storage.getEntry(3));
                assertEquals(0, storage.getTerm(3));
            } else {
                assertEquals(3, storage.getLastLogIndex());
                assertEquals(configuration.getId(), storage.getEntry(3).getId());
                assertEquals(EntryType.ENTRY_TYPE_CONFIGURATION, storage.getEntry(3).getType());
            }
        } finally {
            manager.shutdown();
            manager.join();
            storage.shutdown();
        }
        ConfigurationManager recovered = new ConfigurationManager();
        LogStorage reopened = storage(fileBackend);
        LogManagerImpl restarted = new LogManagerImpl();
        try {
            assertTrue(restarted.init(managerOptions(reopened, recovered, caller)));
            if (fileBackend) {
                assertEquals(new LogId(0, 0), recovered.getLastConfiguration().getId());
                assertTrue(recovered.getLastConfiguration().getConf().isEmpty());
                assertTrue(recovered.getLastConfiguration().getOldConf().isEmpty());
                assertEquals(3, reopened.getFirstLogIndex());
                assertEquals(2, reopened.getLastLogIndex());
                assertNull(reopened.getEntry(3));
            } else {
                assertConfiguration(configuration, recovered);
            }
            restarted.setSnapshot(snapshot);
            assertEquals(new LogId(2, 2), recovered.getSnapshot().getId());
            if (fileBackend) {
                assertEquals(new LogId(2, 2), recovered.getLastConfiguration().getId());
                assertEquals(List.of(new PeerId("127.0.0.1", 8081)),
                    recovered.getLastConfiguration().getConf().listPeers());
                assertSnapshotConfiguration(recovered);
                assertTrue(restarted.checkConsistency().isOk());
                configuration.setId(new LogId(3, 2));
                assertTrue(append(restarted, new ArrayList<>(List.of(configuration))).isOk());
                assertConfiguration(configuration, recovered);
                assertEquals(configuration, reopened.getEntry(3));
                assertEquals(new LogId(2, 2), recovered.getSnapshot().getId());
            } else {
                assertConfiguration(configuration, recovered);
            }
        } finally {
            restarted.shutdown();
            restarted.join();
            reopened.shutdown();
        }
    }
    
    private static void assertConfiguration(LogEntry configuration,
        ConfigurationManager configurations) {
        assertEquals(configuration.getId(), configurations.getLastConfiguration().getId());
        assertEquals(configuration.getPeers(),
            configurations.getLastConfiguration().getConf().listPeers());
        assertEquals(configuration.getLearners(),
            configurations.getLastConfiguration().getConf().listLearners());
        assertEquals(configuration.getOldPeers(),
            configurations.getLastConfiguration().getOldConf().listPeers());
        assertEquals(configuration.getOldLearners(),
            configurations.getLastConfiguration().getOldConf().listLearners());
    }
    
    private static void assertSnapshotConfiguration(ConfigurationManager configurations) {
        assertEquals(List.of(new PeerId("127.0.0.1", 8081)),
            configurations.getLastConfiguration().getConf().listPeers());
        assertEquals(List.of(new PeerId("127.0.0.1", 8086)),
            configurations.getLastConfiguration().getOldConf().listPeers());
        assertEquals(List.of(new PeerId("127.0.0.1", 8087)),
            configurations.getLastConfiguration().getConf().listLearners());
        assertEquals(List.of(new PeerId("127.0.0.1", 8088)),
            configurations.getLastConfiguration().getOldConf().listLearners());
    }
    
    private LogStorage storage(boolean fileBackend) {
        return fileBackend ? new FileLogStorage(directory, new RaftOptions())
            : new RocksDBLogStorage(directory.resolve("rocksdb").toString(), new RaftOptions());
    }
    
    private static LogManagerOptions managerOptions(LogStorage storage,
        ConfigurationManager configurations, FSMCaller caller) {
        LogManagerOptions options = new LogManagerOptions();
        options.setGroupId("file-log-manager");
        options.setLogStorage(storage);
        options.setConfigurationManager(configurations);
        options.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        options.setRaftOptions(new RaftOptions());
        options.setNodeMetrics(new NodeMetrics(false));
        options.setFsmCaller(caller);
        return options;
    }
    
    private static SnapshotMeta snapshot(long index, long term) {
        return SnapshotMeta.newBuilder().setLastIncludedIndex(index).setLastIncludedTerm(term)
            .addPeers("127.0.0.1:8081").addOldPeers("127.0.0.1:8086")
            .addLearners("127.0.0.1:8087").addOldLearners("127.0.0.1:8088").build();
    }
    
    private static List<LogEntry> entries(long first, long last, long term) {
        List<LogEntry> entries = new ArrayList<>();
        for (long index = first; index <= last; index++) {
            LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
            entry.setId(new LogId(index, term));
            entry.setData(ByteBuffer.wrap(new byte[] {(byte) index}));
            entries.add(entry);
        }
        return entries;
    }
    
    private static Status append(LogManager manager, List<LogEntry> entries) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Status> result = new AtomicReference<>();
        manager.appendEntries(entries, new LogManager.StableClosure() {
            
            @Override
            public void run(Status status) {
                result.set(status);
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        return result.get();
    }
}
