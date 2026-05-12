/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.file;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchFileCenterTest {
    
    private static final String PATH =
        Paths.get(System.getProperty("user.home"), "/watch_file_change_test").toString();
    
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(32);
    
    final Object monitor = new Object();
    
    @BeforeAll
    static void beforeCls() throws Exception {
        DiskUtils.deleteDirThenMkdir(PATH);
    }
    
    @AfterAll
    static void afterCls() throws Exception {
        DiskUtils.deleteDirectory(PATH);
    }
    
    @AfterEach
    void afterEach() throws Exception {
        WatchFileCenter.deregisterAllWatcher(PATH);
    }
    
    // The last file change must be notified
    
    @Test
    void testHighConcurrencyModify() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        Set<String> set = new ConcurrentHashSet<>();
        
        final String fileName = "test2_file_change";
        final File file = Paths.get(PATH, fileName).toFile();
        
        func(fileName, file, content -> {
            set.add(content);
            count.incrementAndGet();
        });
        
        ThreadUtils.sleep(5_000L);
    }
    
    @Test
    void testModifyFileMuch() throws Exception {
        final String fileName = "modify_file_much";
        final File file = Paths.get(PATH, fileName).toFile();
        DiskUtils.writeFile(file, ByteUtils.toBytes("start_test"), false);
        
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger(0);
        
        WatchFileCenter.registerWatcher(PATH, new FileWatcher() {
            
            @Override
            public void onChange(FileChangeEvent event) {
                try {
                    System.out.println(event);
                    System.out.println(DiskUtils.readFile(file));
                    count.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, fileName);
            }
        });
        ThreadUtils.sleep(1000L);
        for (int i = 0; i < 3; i++) {
            DiskUtils.writeFile(file, ByteUtils.toBytes(("test_modify_file_" + i)), false);
            ThreadUtils.sleep(10_000L);
        }
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        assertTrue(count.get() >= 3);
    }
    
    @Test
    void testMultiFileModify() throws Exception {
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            AtomicInteger count = new AtomicInteger(0);
            Set<String> set = new ConcurrentHashSet<>();
            
            final String fileName = "test2_file_change_" + i;
            final File file = Paths.get(PATH, fileName).toFile();
            
            EXECUTOR.execute(() -> {
                try {
                    func(fileName, file, content -> {
                        set.add(content);
                        count.incrementAndGet();
                    });
                } catch (Throwable ex) {
                    ex.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        ThreadUtils.sleep(5_000L);
    }
    
    @Test
    void testRegisterNewPathWithException() {
        URL url = getClass().getClassLoader().getResource("application.properties");
        String path = url.getPath();
        assertThrows(IllegalArgumentException.class,
            () -> WatchFileCenter.registerWatcher(path, new FileWatcher() {
                
                @Override
                public void onChange(FileChangeEvent event) {
                    
                }
                
                @Override
                public boolean interest(String context) {
                    return false;
                }
            }));
    }
    
    @Test
    void testDeregisterWatcher() throws NacosException {
        FileWatcher watcher = new FileWatcher() {
            
            @Override
            public void onChange(FileChangeEvent event) {
                
            }
            
            @Override
            public boolean interest(String context) {
                return false;
            }
        };
        assertFalse(WatchFileCenter.deregisterWatcher(PATH, watcher));
        WatchFileCenter.registerWatcher(PATH, watcher);
        assertTrue(WatchFileCenter.deregisterWatcher(PATH, watcher));
    }
    
    @Test
    void testRegisterWatcherAfterShutDown() {
        try {
            FileWatcher fileWatcher = mock(FileWatcher.class);
            assertDoesNotThrow(() -> WatchFileCenter.registerWatcher(PATH, fileWatcher));
            WatchFileCenter.shutdown();
            assertThrows(IllegalStateException.class,
                () -> WatchFileCenter.registerWatcher(PATH, fileWatcher));
        } finally {
            ((AtomicBoolean) ReflectionTestUtils.getField(WatchFileCenter.class, "CLOSED"))
                .set(false);
        }
    }
    
    @Test
    void testCallBackWatcherWithException() throws NacosException {
        FileWatcher fileWatcher = mock(FileWatcher.class);
        when(fileWatcher.interest(anyString())).thenReturn(true);
        doThrow(new RuntimeException("test")).when(fileWatcher)
            .onChange(any(FileChangeEvent.class));
        WatchFileCenter.registerWatcher(PATH, fileWatcher);
        FileWatcher fileWatcher2 = mock(FileWatcher.class);
        when(fileWatcher2.interest(anyString())).thenReturn(true);
        WatchFileCenter.registerWatcher(PATH, fileWatcher2);
        ThreadUtils.sleep(1000L);
        final String fileName = "modify_file_for_exception";
        final File file = Paths.get(PATH, fileName).toFile();
        DiskUtils.writeFile(file, ByteUtils.toBytes("start_test"), false);
        ThreadUtils.sleep(10_000L);
        verify(fileWatcher2, atLeastOnce()).onChange(any(FileChangeEvent.class));
    }
    
    @Test
    void testCallBackWatcherByExecutor() throws NacosException {
        FileWatcher fileWatcher = mock(FileWatcher.class);
        when(fileWatcher.interest(anyString())).thenReturn(true);
        Executor executor = mock(Executor.class);
        doNothing().when(executor).execute(any(Runnable.class));
        when(fileWatcher.executor()).thenReturn(executor);
        WatchFileCenter.registerWatcher(PATH, fileWatcher);
        ThreadUtils.sleep(1000L);
        final String fileName = "modify_file_for_executor";
        final File file = Paths.get(PATH, fileName).toFile();
        DiskUtils.writeFile(file, ByteUtils.toBytes("start_test"), false);
        ThreadUtils.sleep(10_000L);
        verify(executor, atLeastOnce()).execute(any(Runnable.class));
    }
    
    @Test
    void testOverFlowEvent()
        throws NoSuchMethodException, URISyntaxException, NacosException, InvocationTargetException,
        IllegalAccessException {
        File file = new File(getClass().getClassLoader()
            .getResource("test-file-watcher-overflow/test.properties").toURI());
        final String path = file.getParentFile().getAbsolutePath();
        AtomicBoolean containAssert = new AtomicBoolean(false);
        WatchFileCenter.registerWatcher(path, new FileWatcher() {
            
            @Override
            public void onChange(FileChangeEvent event) {
                try {
                    assertEquals("test.properties", event.getContext());
                    assertEquals(path, event.getPaths());
                } catch (AssertionError e) {
                    System.out.println(e.getMessage());
                    containAssert.set(true);
                }
            }
            
            @Override
            public boolean interest(String context) {
                return true;
            }
        });
        Map<String, WatchFileCenter.WatchDirJob> map =
            (Map<String, WatchFileCenter.WatchDirJob>) ReflectionTestUtils.getField(
                WatchFileCenter.class, "MANAGER");
        WatchFileCenter.WatchDirJob job = map.get(path);
        Method method = WatchFileCenter.WatchDirJob.class.getDeclaredMethod("eventOverflow");
        method.setAccessible(true);
        method.invoke(job);
        assertFalse(containAssert.get());
    }
    
    @Test
    void testConstructor() {
        new WatchFileCenter();
    }
    
    @Test
    void testShutdownIsIdempotent() {
        try {
            assertDoesNotThrow(WatchFileCenter::shutdown);
            assertDoesNotThrow(WatchFileCenter::shutdown);
        } finally {
            ((AtomicBoolean) ReflectionTestUtils.getField(WatchFileCenter.class, "CLOSED"))
                .set(false);
        }
    }
    
    @Test
    void testRegisterReturnsFalseWhenAtMaxLimit() throws NacosException {
        Integer originalCnt =
            (Integer) ReflectionTestUtils.getField(WatchFileCenter.class, "NOW_WATCH_JOB_CNT");
        Integer max = (Integer) ReflectionTestUtils.getField(WatchFileCenter.class,
            "MAX_WATCH_FILE_JOB");
        try {
            ReflectionTestUtils.setField(WatchFileCenter.class, "NOW_WATCH_JOB_CNT", max);
            FileWatcher watcher = mock(FileWatcher.class);
            assertFalse(WatchFileCenter.registerWatcher(PATH, watcher));
        } finally {
            ReflectionTestUtils.setField(WatchFileCenter.class, "NOW_WATCH_JOB_CNT", originalCnt);
        }
    }
    
    @Test
    void testShutdownContinuesWhenJobShutdownThrows() throws Exception {
        Map<String, WatchFileCenter.WatchDirJob> manager =
            (Map<String, WatchFileCenter.WatchDirJob>) ReflectionTestUtils.getField(
                WatchFileCenter.class, "MANAGER");
        WatchFileCenter.WatchDirJob faulty = mock(WatchFileCenter.WatchDirJob.class);
        doThrow(new RuntimeException("forced")).when(faulty).shutdown();
        manager.put("__forced__", faulty);
        try {
            assertDoesNotThrow(WatchFileCenter::shutdown);
            verify(faulty).shutdown();
        } finally {
            ((AtomicBoolean) ReflectionTestUtils.getField(WatchFileCenter.class, "CLOSED"))
                .set(false);
            manager.clear();
            ReflectionTestUtils.setField(WatchFileCenter.class, "NOW_WATCH_JOB_CNT", 0);
        }
    }
    
    @Test
    void testRunCatchesUnexpectedThrowable() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            when(mockWs.take()).thenAnswer(inv -> {
                ReflectionTestUtils.setField(job, "watch", false);
                throw new RuntimeException("forced");
            });
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            job.run();
            verify(mockWs, atLeastOnce()).take();
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testRunSkipsEmptyEvents() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            WatchKey emptyKey = mock(WatchKey.class);
            when(emptyKey.pollEvents()).thenReturn(Collections.emptyList());
            when(mockWs.take()).thenAnswer(inv -> {
                ReflectionTestUtils.setField(job, "watch", false);
                return emptyKey;
            });
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            job.run();
            verify(emptyKey).pollEvents();
            verify(emptyKey).reset();
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testRunReturnsWhenExecutorAlreadyShutdown() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            ExecutorService mockExec = mock(ExecutorService.class);
            when(mockExec.isShutdown()).thenReturn(true);
            WatchKey withEvents = mock(WatchKey.class);
            WatchEvent<?> event = mock(WatchEvent.class);
            when(withEvents.pollEvents()).thenAnswer(inv -> Collections.singletonList(event));
            when(mockWs.take()).thenReturn(withEvents);
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            ReflectionTestUtils.setField(job, "callBackExecutor", mockExec);
            job.run();
            verify(mockExec).isShutdown();
            verify(mockExec, never()).execute(any(Runnable.class));
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testRunDispatchesEventOverflowToExecutor() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            ExecutorService inlineExec = mock(ExecutorService.class);
            when(inlineExec.isShutdown()).thenReturn(false);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            }).when(inlineExec).execute(any(Runnable.class));
            WatchEvent<?> overflowEvent = mock(WatchEvent.class);
            when(overflowEvent.kind()).thenAnswer(inv -> StandardWatchEventKinds.OVERFLOW);
            WatchKey overflowKey = mock(WatchKey.class);
            when(overflowKey.pollEvents())
                .thenAnswer(inv -> Collections.singletonList(overflowEvent));
            when(mockWs.take()).thenAnswer(inv -> {
                ReflectionTestUtils.setField(job, "watch", false);
                return overflowKey;
            });
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            ReflectionTestUtils.setField(job, "callBackExecutor", inlineExec);
            // ensure listFiles() inside eventOverflow returns empty (no NPE) by pointing paths at
            // an empty real dir
            String overflowDir = createEmptyTempDir("watch_file_overflow_run_");
            ReflectionTestUtils.setField(job, "paths", overflowDir);
            try {
                job.run();
                verify(inlineExec).execute(any(Runnable.class));
            } finally {
                DiskUtils.deleteDirectory(overflowDir);
            }
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testRunSwallowsClosedWatchServiceException() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            when(mockWs.take()).thenAnswer(inv -> {
                ReflectionTestUtils.setField(job, "watch", false);
                throw new ClosedWatchServiceException();
            });
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            assertDoesNotThrow(job::run);
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testJobShutdownIgnoresIoExceptionOnWatchServiceClose() throws Exception {
        WatchFileCenter.WatchDirJob job = newDetachedJob();
        try {
            WatchService mockWs = mock(WatchService.class);
            doThrow(new IOException("forced")).when(mockWs).close();
            ReflectionTestUtils.setField(job, "watchService", mockWs);
            assertDoesNotThrow(job::shutdown);
        } finally {
            shutdownDetachedJob(job);
        }
    }
    
    @Test
    void testEventOverflowSkipsSubdirectory() throws Exception {
        String dirPath = Paths.get(System.getProperty("user.home"),
            "watch_file_overflow_subdir_" + System.nanoTime()).toString();
        DiskUtils.deleteDirThenMkdir(dirPath);
        File subDir = Paths.get(dirPath, "sub").toFile();
        assertTrue(subDir.mkdir());
        File regularFile = Paths.get(dirPath, "child.properties").toFile();
        DiskUtils.touch(regularFile);
        AtomicReference<String> seen = new AtomicReference<>(null);
        try {
            WatchFileCenter.registerWatcher(dirPath, new FileWatcher() {
                
                @Override
                public void onChange(FileChangeEvent event) {
                    seen.set(String.valueOf(event.getContext()));
                }
                
                @Override
                public boolean interest(String context) {
                    return true;
                }
            });
            Map<String, WatchFileCenter.WatchDirJob> map =
                (Map<String, WatchFileCenter.WatchDirJob>) ReflectionTestUtils.getField(
                    WatchFileCenter.class, "MANAGER");
            WatchFileCenter.WatchDirJob job = map.get(dirPath);
            Method method = WatchFileCenter.WatchDirJob.class.getDeclaredMethod("eventOverflow");
            method.setAccessible(true);
            method.invoke(job);
            ThreadUtils.sleep(500L);
            assertEquals("child.properties", seen.get());
        } finally {
            WatchFileCenter.deregisterAllWatcher(dirPath);
            DiskUtils.deleteDirectory(dirPath);
        }
    }
    
    private static WatchFileCenter.WatchDirJob newDetachedJob() throws Exception {
        String dirPath = createEmptyTempDir("watch_file_detached_");
        Constructor<WatchFileCenter.WatchDirJob> ctor =
            WatchFileCenter.WatchDirJob.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(dirPath);
    }
    
    private static void shutdownDetachedJob(WatchFileCenter.WatchDirJob job) {
        try {
            ExecutorService exec = (ExecutorService) ReflectionTestUtils.getField(job,
                "callBackExecutor");
            if (exec != null && !exec.isShutdown()) {
                exec.shutdownNow();
            }
        } catch (Throwable ignore) {
            // best-effort cleanup
        }
        Object pathField = ReflectionTestUtils.getField(job, "paths");
        if (pathField instanceof String) {
            try {
                DiskUtils.deleteDirectory((String) pathField);
            } catch (IOException ignore) {
                // best-effort cleanup
            }
        }
    }
    
    private static String createEmptyTempDir(String prefix) throws Exception {
        String dirPath = Paths.get(System.getProperty("java.io.tmpdir"),
            prefix + System.nanoTime()).toString();
        DiskUtils.deleteDirThenMkdir(dirPath);
        return dirPath;
    }
    
    private void func(final String fileName, final File file, final Consumer<String> consumer)
        throws Exception {
        CountDownLatch latch = new CountDownLatch(100);
        DiskUtils.touch(file);
        WatchFileCenter.registerWatcher(PATH, new FileWatcher() {
            
            @Override
            public void onChange(FileChangeEvent event) {
                final File file = Paths.get(PATH, fileName).toFile();
                final String content = DiskUtils.readFile(file);
                consumer.accept(content);
            }
            
            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, fileName);
            }
        });
        
        final AtomicInteger id = new AtomicInteger(0);
        final AtomicReference<String> finalContent = new AtomicReference<>(null);
        for (int i = 0; i < 100; i++) {
            EXECUTOR.execute(() -> {
                final String j = fileName + "_" + id.incrementAndGet();
                try {
                    final File file1 = Paths.get(PATH, fileName).toFile();
                    synchronized (monitor) {
                        finalContent.set(j);
                        DiskUtils.writeFile(file1, j.getBytes(StandardCharsets.UTF_8), false);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
    }
    
}
