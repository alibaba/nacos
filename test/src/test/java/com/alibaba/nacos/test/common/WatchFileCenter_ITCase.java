/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.common;

import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class WatchFileCenter_ITCase {
    
    static final String path = Paths.get(System.getProperty("user.home"), "/watch_file_change_test").toString();
    
    final Object monitor = new Object();
    
    static final Executor executor = Executors.newFixedThreadPool(32);
    
    @BeforeClass
    public static void beforeCls() throws Exception {
        DiskUtils.deleteDirThenMkdir(path);
    }
    
    @AfterClass
    public static void afterCls() throws Exception {
        DiskUtils.deleteDirectory(path);
    }
    
    // The last file change must be notified
    
    @Test
    public void test_high_concurrency_modify() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        Set<String> set = new ConcurrentHashSet<>();
        
        final String fileName = "test2_file_change";
        final File file = Paths.get(path, fileName).toFile();
        
        func(fileName, file, content -> {
            set.add(content);
            count.incrementAndGet();
        });
        
        ThreadUtils.sleep(5_000L);
    }
    
    @Test
    public void test_modify_file_much() throws Exception {
        final String fileName = "modify_file_much";
        final File file = Paths.get(path, fileName).toFile();
        
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger count = new AtomicInteger(0);
        
        WatchFileCenter.registerWatcher(path, new FileWatcher() {
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
        
        for (int i = 0; i < 3; i++) {
            DiskUtils.writeFile(file, ByteUtils.toBytes(("test_modify_file_" + i)), false);
            ThreadUtils.sleep(10_000L);
        }
        
        latch.await(10_000L, TimeUnit.MILLISECONDS);
        
        Assert.assertEquals(3, count.get());
    }
    
    @Test
    public void test_multi_file_modify() throws Exception {
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            AtomicInteger count = new AtomicInteger(0);
            Set<String> set = new ConcurrentHashSet<>();
            
            final String fileName = "test2_file_change_" + i;
            final File file = Paths.get(path, fileName).toFile();
            
            executor.execute(() -> {
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
    
    private void func(final String fileName, final File file, final Consumer<String> consumer) throws Exception {
        CountDownLatch latch = new CountDownLatch(100);
        DiskUtils.touch(file);
        WatchFileCenter.registerWatcher(path, new FileWatcher() {
            @Override
            public void onChange(FileChangeEvent event) {
                final File file = Paths.get(path, fileName).toFile();
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
            executor.execute(() -> {
                final String j = fileName + "_" + id.incrementAndGet();
                try {
                    final File file1 = Paths.get(path, fileName).toFile();
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
