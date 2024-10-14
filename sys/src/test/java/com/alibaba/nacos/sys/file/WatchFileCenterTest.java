/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchFileCenterTest {
    
    private static final String PATH = Paths.get(System.getProperty("user.home"), "/watch_file_change_test").toString();
    
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
        assertThrows(IllegalArgumentException.class, () -> WatchFileCenter.registerWatcher(path, new FileWatcher() {
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
            assertThrows(IllegalStateException.class, () -> WatchFileCenter.registerWatcher(PATH, fileWatcher));
        } finally {
            ((AtomicBoolean) ReflectionTestUtils.getField(WatchFileCenter.class, "CLOSED")).set(false);
        }
    }
    
    @Test
    void testCallBackWatcherWithException() throws NacosException {
        FileWatcher fileWatcher = mock(FileWatcher.class);
        when(fileWatcher.interest(anyString())).thenReturn(true);
        doThrow(new RuntimeException("test")).when(fileWatcher).onChange(any(FileChangeEvent.class));
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
            throws NoSuchMethodException, URISyntaxException, NacosException, InvocationTargetException, IllegalAccessException {
        File file = new File(getClass().getClassLoader().getResource("test-file-watcher-overflow/test.properties").toURI());
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
        Map<String, WatchFileCenter.WatchDirJob> map = (Map<String, WatchFileCenter.WatchDirJob>) ReflectionTestUtils.getField(
                WatchFileCenter.class, "MANAGER");
        WatchFileCenter.WatchDirJob job = map.get(path);
        Method method = WatchFileCenter.WatchDirJob.class.getDeclaredMethod("eventOverflow");
        method.setAccessible(true);
        method.invoke(job);
        assertFalse(containAssert.get());
    }
    
    private void func(final String fileName, final File file, final Consumer<String> consumer) throws Exception {
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