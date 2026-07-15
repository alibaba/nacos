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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceInfoDiskCacheRefresherTest {
    
    @Test
    void testPublishEventOnlyWriteLatestSnapshot() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        Map<String, ServiceInfo> writtenData = new ConcurrentHashMap<>();
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> {
            writeCount.incrementAndGet();
            writtenData.put(serviceInfo.getKeyWithoutClusters(), serviceInfo);
            return true;
        });
        try {
            ServiceInfo first = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
            ServiceInfo second = createServiceInfo("a@@b@@c", "1.1.1.2", 2);
            refresher.publishEvent(
                new ServiceInfoDiskCacheRefreshEvent(first.getKeyWithoutClusters(), first,
                    "cache"));
            refresher
                .publishEvent(new ServiceInfoDiskCacheRefreshEvent(second.getKeyWithoutClusters(),
                    second, "cache"));
            
            refresher.flushNow();
            
            assertEquals(1, writeCount.get());
            assertEquals("1.1.1.2", writtenData.get("a@@b").getHosts().get(0).getIp());
            assertEquals(0, refresher.pendingEventSize());
        } finally {
            refresher.shutdown();
        }
    }
    
    @Test
    void testDefaultConstructorShutdownFlushPendingEvents() throws Exception {
        ServiceInfoDiskCacheRefresher refresher = new ServiceInfoDiskCacheRefresher();
        Path cacheDir = Files.createTempDirectory("service-info-disk-cache-refresher");
        try {
            ServiceInfo serviceInfo = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
            refresher.publishEvent(
                new ServiceInfoDiskCacheRefreshEvent(serviceInfo.getKeyWithoutClusters(),
                    serviceInfo,
                    cacheDir.toString()));
            
            refresher.shutdown();
            
            assertEquals(0, refresher.pendingEventSize());
            assertTrue(Files.exists(cacheDir.resolve(serviceInfo.getKeyEncoded())));
        } finally {
            if (!refresher.isShutdown()) {
                refresher.shutdown();
            }
        }
    }
    
    @Test
    void testFlushDifferentServiceKeys() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> {
            writeCount.incrementAndGet();
            return true;
        });
        try {
            ServiceInfo first = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
            ServiceInfo second = createServiceInfo("d@@e@@f", "1.1.1.2", 2);
            refresher.publishEvent(
                new ServiceInfoDiskCacheRefreshEvent(first.getKeyWithoutClusters(), first,
                    "cache"));
            refresher
                .publishEvent(new ServiceInfoDiskCacheRefreshEvent(second.getKeyWithoutClusters(),
                    second, "cache"));
            
            refresher.flushNow();
            
            assertEquals(2, writeCount.get());
            assertEquals(0, refresher.pendingEventSize());
        } finally {
            refresher.shutdown();
        }
    }
    
    @Test
    void testFlushFailedEventRetry() throws Exception {
        AtomicBoolean firstFailure = new AtomicBoolean(true);
        AtomicInteger successWriteCount = new AtomicInteger();
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> {
            if ("a@@b".equals(serviceInfo.getKeyWithoutClusters())
                && firstFailure.getAndSet(false)) {
                return false;
            }
            successWriteCount.incrementAndGet();
            return true;
        });
        try {
            ServiceInfo failed = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
            ServiceInfo success = createServiceInfo("d@@e@@f", "1.1.1.2", 2);
            refresher
                .publishEvent(new ServiceInfoDiskCacheRefreshEvent(failed.getKeyWithoutClusters(),
                    failed, "cache"));
            refresher
                .publishEvent(new ServiceInfoDiskCacheRefreshEvent(success.getKeyWithoutClusters(),
                    success, "cache"));
            
            refresher.flushNow();
            assertEquals(1, refresher.pendingEventSize());
            assertEquals(1, successWriteCount.get());
            
            refresher.flushNow();
            assertEquals(0, refresher.pendingEventSize());
            assertEquals(2, successWriteCount.get());
        } finally {
            refresher.shutdown();
        }
    }
    
    @Test
    void testFlushWriterExceptionWillBeCaught() throws Exception {
        AtomicBoolean shouldThrow = new AtomicBoolean(true);
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> {
            if (shouldThrow.get()) {
                throw new IllegalStateException("test");
            }
            return true;
        });
        try {
            ServiceInfo serviceInfo = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
            refresher.publishEvent(
                new ServiceInfoDiskCacheRefreshEvent(serviceInfo.getKeyWithoutClusters(),
                    serviceInfo,
                    "cache"));
            
            refresher.flushNow();
            
            assertEquals(1, refresher.pendingEventSize());
        } finally {
            shouldThrow.set(false);
            refresher.shutdown();
        }
    }
    
    @Test
    void testShutdownFlushPendingEvents() throws Exception {
        AtomicInteger writeCount = new AtomicInteger();
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> {
            writeCount.incrementAndGet();
            return true;
        });
        ServiceInfo serviceInfo = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
        refresher.publishEvent(
            new ServiceInfoDiskCacheRefreshEvent(serviceInfo.getKeyWithoutClusters(), serviceInfo,
                "cache"));
        
        refresher.shutdown();
        
        assertEquals(1, writeCount.get());
        assertTrue(refresher.isShutdown());
        assertEquals(0, refresher.pendingEventSize());
    }
    
    @Test
    void testShutdownTimeoutWillExit() throws Exception {
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> true);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        ScheduledThreadPoolExecutor refreshExecutor = getRefreshExecutor(refresher);
        refreshExecutor.execute(() -> {
            taskStarted.countDown();
            try {
                releaseTask.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));
        try {
            refresher.shutdown();
            assertTrue(refresher.isShutdown());
        } finally {
            releaseTask.countDown();
            refreshExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }
    
    @Test
    void testShutdownWhenInterruptedThrowException() throws Exception {
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> true);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        ScheduledThreadPoolExecutor refreshExecutor = getRefreshExecutor(refresher);
        refreshExecutor.execute(() -> {
            taskStarted.countDown();
            try {
                releaseTask.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));
        try {
            Thread.currentThread().interrupt();
            assertThrows(com.alibaba.nacos.api.exception.NacosException.class, refresher::shutdown);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(refresher.isShutdown());
        } finally {
            Thread.interrupted();
            releaseTask.countDown();
            refreshExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }
    
    @Test
    void testShutdownWithRepeatedFailureWillExit() throws Exception {
        ServiceInfoDiskCacheRefresher refresher = createRefresher((serviceInfo, cacheDir) -> false);
        ServiceInfo serviceInfo = createServiceInfo("a@@b@@c", "1.1.1.1", 1);
        refresher.publishEvent(
            new ServiceInfoDiskCacheRefreshEvent(serviceInfo.getKeyWithoutClusters(), serviceInfo,
                "cache"));
        
        refresher.shutdown();
        
        assertTrue(refresher.isShutdown());
        assertEquals(1, refresher.pendingEventSize());
    }
    
    private ServiceInfoDiskCacheRefresher createRefresher(
        ServiceInfoDiskCacheRefresher.DiskCacheWriter writer) {
        return new ServiceInfoDiskCacheRefresher(TimeUnit.DAYS.toMillis(1), 50L, writer);
    }
    
    private ScheduledThreadPoolExecutor getRefreshExecutor(ServiceInfoDiskCacheRefresher refresher)
        throws NoSuchFieldException, IllegalAccessException {
        Field refreshExecutorField =
            ServiceInfoDiskCacheRefresher.class.getDeclaredField("refreshExecutor");
        refreshExecutorField.setAccessible(true);
        return (ScheduledThreadPoolExecutor) refreshExecutorField.get(refresher);
    }
    
    private ServiceInfo createServiceInfo(String serviceKey, String ip, int port) {
        ServiceInfo serviceInfo = new ServiceInfo(serviceKey);
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        serviceInfo.setHosts(Collections.singletonList(instance));
        return serviceInfo;
    }
}
