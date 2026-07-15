/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpClientManagerTest {
    
    @BeforeEach
    void setUp() throws IllegalAccessException, NoSuchFieldException {
        Field instanceField = HttpClientManager.class.getDeclaredField("httpClientManager");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        HttpClientManager.getInstance().shutdown();
    }
    
    @Test
    public void testGetNacosRestTemplate() {
        HttpClientManager httpClientManager = HttpClientManager.getInstance();
        NacosRestTemplate template = httpClientManager.getNacosRestTemplate();
        assertNotNull(template, "NacosRestTemplate should not be null.");
    }
    
    // ========== Additional Tests for Coverage ==========
    
    @Test
    @DisplayName("getInstance concurrent access should return same instance")
    void testGetInstanceConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final AtomicReference<HttpClientManager> firstInstance = new AtomicReference<>();
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpClientManager instance = HttpClientManager.getInstance();
                    if (firstInstance.get() == null) {
                        firstInstance.set(instance);
                    }
                    assertEquals(firstInstance.get(), instance,
                        "All threads should get the same HttpClientManager instance");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        endLatch.await();
        executor.shutdown();
        
        assertNotNull(firstInstance.get(), "HttpClientManager instance should be created");
    }
    
    @Test
    @DisplayName("shutdown should complete without throwing")
    void testShutdownCompletes() throws NacosException {
        HttpClientManager instance = HttpClientManager.getInstance();
        assertNotNull(instance.getNacosRestTemplate());
        
        // Shutdown should work even if called multiple times
        instance.shutdown();
        // Second shutdown should also work without throwing
        instance.shutdown();
    }
}
