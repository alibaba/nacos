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

package com.alibaba.nacos.istio;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.mcp.ServiceEntryMcpGenerator;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushRequest;
import com.alibaba.nacos.istio.xds.ServiceEntryXdsGenerator;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for #16022: {@code ServiceEntryMcpGenerator} and {@code ServiceEntryXdsGenerator}
 * are singletons that kept their working list in a shared field, so concurrent {@code generate()}
 * calls corrupted each other's results with {@code ConcurrentModificationException},
 * {@code ArrayIndexOutOfBoundsException} or a wrong number of resources.
 */
class ServiceEntryGeneratorConcurrencyTest {
    
    private static final int SERVICE_COUNT = 200;
    
    private static final int CALLS_PER_THREAD = 1000;
    
    private PushRequest buildPushRequest() {
        Map<String, IstioService> services = new HashMap<>();
        for (int i = 0; i < SERVICE_COUNT; i++) {
            Instance instance = new Instance();
            instance.setIp("10.0.0." + i);
            instance.setPort(8080);
            ServiceInfo info = new ServiceInfo();
            info.setName("service-" + i);
            info.setGroupName("DEFAULT_GROUP");
            info.setHosts(List.of(instance));
            services.put("service-" + i, new IstioService(
                Service.newService("public", "DEFAULT_GROUP", "service-" + i), info));
        }
        ResourceSnapshot snapshot = new ResourceSnapshot(new IstioConfig());
        snapshot.getIstioResources().setIstioServiceMap(services);
        // deltaGenerate() stamps each resource with the snapshot version, which is
        // normally assigned by initResourceSnapshot() during a push; set it here so
        // the protobuf builder does not reject a null version.
        try {
            java.lang.reflect.Field versionField =
                ResourceSnapshot.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(snapshot, "test-version");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return new PushRequest(snapshot, true);
    }
    
    private void runConcurrently(Runnable calls) throws InterruptedException {
        Thread first = new Thread(calls);
        Thread second = new Thread(calls);
        first.start();
        second.start();
        first.join();
        second.join();
    }
    
    @Test
    void concurrentMcpGenerateReturnsAllServices() throws Exception {
        PushRequest request = buildPushRequest();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger wrongSize = new AtomicInteger();
        Runnable calls = () -> {
            for (int i = 0; i < CALLS_PER_THREAD; i++) {
                try {
                    if (ServiceEntryMcpGenerator.getInstance().generate(request)
                        .size() != SERVICE_COUNT) {
                        wrongSize.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
            }
        };
        runConcurrently(calls);
        assertEquals(0, failed.get(), "concurrent generate() calls must not throw");
        assertEquals(0, wrongSize.get(), "every generate() call must return all services");
    }
    
    @Test
    void concurrentXdsGenerateReturnsAllServices() throws Exception {
        PushRequest request = buildPushRequest();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger wrongSize = new AtomicInteger();
        Runnable calls = () -> {
            for (int i = 0; i < CALLS_PER_THREAD; i++) {
                try {
                    if (ServiceEntryXdsGenerator.getInstance().generate(request)
                        .size() != SERVICE_COUNT) {
                        wrongSize.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
            }
        };
        runConcurrently(calls);
        assertEquals(0, failed.get(), "concurrent generate() calls must not throw");
        assertEquals(0, wrongSize.get(), "every generate() call must return all services");
    }
    
    @Test
    void concurrentXdsDeltaGenerateReturnsAllServices() throws Exception {
        PushRequest request = buildPushRequest();
        request.setFull(false);
        request.setSubscribe(new HashSet<>());
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger wrongSize = new AtomicInteger();
        Runnable calls = () -> {
            for (int i = 0; i < CALLS_PER_THREAD; i++) {
                try {
                    if (ServiceEntryXdsGenerator.getInstance().deltaGenerate(request)
                        .size() != SERVICE_COUNT) {
                        wrongSize.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
            }
        };
        runConcurrently(calls);
        assertEquals(0, failed.get(), "concurrent deltaGenerate() calls must not throw");
        assertEquals(0, wrongSize.get(), "every deltaGenerate() call must return all services");
    }
}
