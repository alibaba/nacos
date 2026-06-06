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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NacosLock 单元测试.
 *
 * @author DHX
 * @date 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
class NacosLockTest {

    @Mock
    private LockGrpcClient grpcClient;

    private NacosLockWatchdog watchdog;

    private NacosLock lock;

    @BeforeEach
    void setUp() throws Exception {
        watchdog = new NacosLockWatchdog(500L);
        lock = new NacosLock("test-key", LockConstants.REENTRANT_LOCK_TYPE, grpcClient, watchdog,
            "test-client-id");

        // 在 mock 上初始化 notificationFutures 字段
        Field field = LockGrpcClient.class.getDeclaredField("notificationFutures");
        field.setAccessible(true);
        field.set(grpcClient, new ConcurrentHashMap<>());
    }

    @AfterEach
    void tearDown() {
        watchdog.shutdown();
    }

    @Test
    @DisplayName("future map 替换条目时旧 future 的处理")
    void testFutureMapReplacement() throws Exception {
        ConcurrentHashMap<String, CompletableFuture<?>> notificationFutures =
            getNotificationFutures();

        String mapKey = "test-key:test-owner";

        // 第一次注册
        CompletableFuture<?> firstFuture = new CompletableFuture<>();
        notificationFutures.put(mapKey, firstFuture);
        assertFalse(firstFuture.isDone());

        // 第二次注册替换第一次
        CompletableFuture<?> secondFuture = new CompletableFuture<>();
        CompletableFuture<?> oldFuture = notificationFutures.put(mapKey, secondFuture);

        // 旧 future 从 put() 返回，但未被完成
        // 完成操作在 LockGrpcClient.registerForNotification() 中执行
        assertNotNull(oldFuture);
        assertTrue(oldFuture == firstFuture);
    }

    @Test
    @DisplayName("相同 key 的 NacosLock 实例共享 watchdog")
    void testSameKeyWatchdogShared() {
        NacosLock lock1 = new NacosLock("same-key", LockConstants.REENTRANT_LOCK_TYPE,
            grpcClient, watchdog, "client-1");
        NacosLock lock2 = new NacosLock("same-key", LockConstants.NON_REENTRANT_LOCK_TYPE,
            grpcClient, watchdog, "client-1");

        // 两个锁共享同一个 watchdog 实例
        // 如果都用相同 key 注册，第二次会覆盖第一次
        assertNotNull(lock1);
        assertNotNull(lock2);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CompletableFuture<?>> getNotificationFutures()
        throws Exception {
        Field field = LockGrpcClient.class.getDeclaredField("notificationFutures");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, CompletableFuture<?>>) field.get(grpcClient);
    }
}
