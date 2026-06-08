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

package com.alibaba.nacos.lock.persistence;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.NacosLockManager;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.model.LockKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosLockSnapshotOperationTest {
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private ExecutorService testSnapshotExecutor;
    
    private ExecutorService originalSnapshotExecutor;
    
    @BeforeEach
    void setUp() throws Exception {
        Field field = RaftExecutor.class.getDeclaredField("raftSnapshotExecutor");
        field.setAccessible(true);
        originalSnapshotExecutor = (ExecutorService) field.get(null);
        testSnapshotExecutor = Executors.newSingleThreadExecutor();
        field.set(null, testSnapshotExecutor);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        Field field = RaftExecutor.class.getDeclaredField("raftSnapshotExecutor");
        field.setAccessible(true);
        field.set(null, originalSnapshotExecutor);
        testSnapshotExecutor.shutdownNow();
    }
    
    @Test
    void testLoadSnapshotAcceptsHashMapSnapshotData() throws Exception {
        NacosLockManager lockManager = new NacosLockManager();
        NacosLockSnapshotOperation operation = newOperation(lockManager);
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "key");
        Map<LockKey, AtomicLockService> snapshotData = new HashMap<>();
        snapshotData.put(lockKey, new MutexAtomicLock("key"));
        
        operation.loadSnapshot(serializer.serialize(snapshotData));
        
        assertTrue(lockManager.getRawLockMap().containsKey(lockKey));
        assertEquals(1, lockManager.getRawLockMap().size());
    }
    
    @Test
    void testLoadSnapshotRequiresNacosLockManager() {
        LockManager lockManager = mock(LockManager.class);
        when(lockManager.showLocks()).thenReturn(Collections.emptyMap());
        NacosLockSnapshotOperation operation = newOperation(lockManager);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> operation.loadSnapshot(serializer.serialize(new HashMap<>())));
        
        assertTrue(exception.getMessage().contains("LockManager must be NacosLockManager"));
    }
    
    @Test
    void testMigrateMutexAtomicLocksContinuesWhenLegacyMigrationFails() throws Exception {
        NacosLockSnapshotOperation operation = newOperation(new NacosLockManager());
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "key");
        Map<LockKey, AtomicLockService> lockMap = new HashMap<>();
        lockMap.put(lockKey, new BrokenMutexAtomicLock("key"));
        
        Method method = NacosLockSnapshotOperation.class
            .getDeclaredMethod("migrateMutexAtomicLocks",
                java.util.concurrent.ConcurrentHashMap.class);
        method.setAccessible(true);
        method.invoke(operation, new java.util.concurrent.ConcurrentHashMap<>(lockMap));
        
        assertEquals(1, lockMap.size());
    }
    
    @Test
    void testSnapshotTags() {
        NacosLockSnapshotOperation operation = newOperation(new NacosLockManager());
        
        assertEquals("NacosLockSnapshotOperation.SAVE", operation.getSnapshotSaveTag());
        assertEquals("NacosLockSnapshotOperation.LOAD", operation.getSnapshotLoadTag());
    }
    
    @Test
    void testDumpSnapshot() throws Exception {
        NacosLockManager lockManager = new NacosLockManager();
        lockManager.getRawLockMap()
            .put(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key"), new MutexAtomicLock("key"));
        NacosLockSnapshotOperation operation = newOperation(lockManager);
        
        try (InputStream inputStream = operation.dumpSnapshot()) {
            assertTrue(inputStream.read() >= 0);
        }
    }
    
    @Test
    void testWriteAndReadSnapshot(@TempDir Path tempDir) throws Exception {
        NacosLockManager sourceManager = new NacosLockManager();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "key");
        sourceManager.getRawLockMap().put(lockKey, new MutexAtomicLock("key"));
        NacosLockSnapshotOperation sourceOperation = newOperation(sourceManager);
        Writer writer = new Writer(tempDir.toString());
        
        assertTrue(sourceOperation.writeSnapshot(writer));
        assertTrue(Files.exists(tempDir.resolve("nacos_lock.zip")));
        
        NacosLockManager targetManager = new NacosLockManager();
        NacosLockSnapshotOperation targetOperation = newOperation(targetManager);
        Reader reader = new Reader(tempDir.toString(), writer.listFiles());
        
        assertTrue(targetOperation.readSnapshot(reader));
        assertTrue(targetManager.getRawLockMap().containsKey(lockKey));
    }
    
    @Test
    void testOnSnapshotSaveSuccess(@TempDir Path tempDir) throws Exception {
        NacosLockSnapshotOperation operation = newOperation(new NacosLockManager());
        Writer writer = new Writer(tempDir.toString());
        CompletableFuture<Boolean> callback = new CompletableFuture<>();
        
        operation.onSnapshotSave(writer, (success, throwable) -> {
            assertNull(throwable);
            callback.complete(success);
        });
        
        assertTrue(callback.get(5, TimeUnit.SECONDS));
    }
    
    @Test
    void testOnSnapshotSaveFailure(@TempDir Path tempDir) throws Exception {
        NacosLockSnapshotOperation operation = new NacosLockSnapshotOperation(
            new NacosLockManager(), new ReentrantReadWriteLock().writeLock()) {
            
            @Override
            boolean writeSnapshot(Writer writer) throws IOException {
                throw new IOException("failed");
            }
        };
        CompletableFuture<Boolean> callback = new CompletableFuture<>();
        
        operation.onSnapshotSave(new Writer(tempDir.toString()),
            (success, throwable) -> callback.complete(!success && throwable != null));
        
        assertTrue(callback.get(5, TimeUnit.SECONDS));
    }
    
    @Test
    void testOnSnapshotLoadSuccess(@TempDir Path tempDir) throws Exception {
        NacosLockManager sourceManager = new NacosLockManager();
        LockKey lockKey = new LockKey(LockConstants.NACOS_LOCK_TYPE, "key");
        sourceManager.getRawLockMap().put(lockKey, new MutexAtomicLock("key"));
        NacosLockSnapshotOperation sourceOperation = newOperation(sourceManager);
        Writer writer = new Writer(tempDir.toString());
        assertTrue(sourceOperation.writeSnapshot(writer));
        
        NacosLockManager targetManager = new NacosLockManager();
        NacosLockSnapshotOperation targetOperation = newOperation(targetManager);
        Reader reader = new Reader(tempDir.toString(), writer.listFiles());
        
        assertTrue(targetOperation.onSnapshotLoad(reader));
        assertTrue(targetManager.getRawLockMap().containsKey(lockKey));
    }
    
    @Test
    void testReadSnapshotWithoutChecksumMeta(@TempDir Path tempDir) throws Exception {
        NacosLockSnapshotOperation sourceOperation = newOperation(new NacosLockManager());
        Writer writer = new Writer(tempDir.toString());
        assertTrue(sourceOperation.writeSnapshot(writer));
        Reader reader = new Reader(tempDir.toString(),
            Collections.singletonMap("nacos_lock.zip", new LocalFileMeta()));
        
        assertTrue(newOperation(new NacosLockManager()).readSnapshot(reader));
    }
    
    @Test
    void testReadSnapshotChecksumMismatch(@TempDir Path tempDir) throws Exception {
        NacosLockSnapshotOperation operation = newOperation(new NacosLockManager());
        Writer writer = new Writer(tempDir.toString());
        operation.writeSnapshot(writer);
        LocalFileMeta wrongMeta = new LocalFileMeta().append("checksum", "wrong");
        Reader reader = new Reader(tempDir.toString(),
            Collections.singletonMap("nacos_lock.zip", wrongMeta));
        
        assertThrows(IllegalArgumentException.class, () -> operation.readSnapshot(reader));
    }
    
    @Test
    void testOnSnapshotLoadReturnsFalseWhenReadFails() {
        NacosLockSnapshotOperation operation = newOperation(new NacosLockManager());
        
        Reader reader = new Reader("/path/not/exist", Collections.emptyMap());
        assertFalse(operation.onSnapshotLoad(reader));
    }
    
    private NacosLockSnapshotOperation newOperation(LockManager lockManager) {
        return new NacosLockSnapshotOperation(lockManager,
            new ReentrantReadWriteLock().writeLock());
    }
    
    private static class BrokenMutexAtomicLock extends MutexAtomicLock {
        
        private static final long serialVersionUID = -1L;
        
        BrokenMutexAtomicLock(String key) {
            super(key);
        }
        
        @Override
        public void migrateFromLegacy() {
            throw new IllegalStateException("migration failed");
        }
    }
}
