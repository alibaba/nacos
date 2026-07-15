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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ExtractorManager} unit test.
 */
class ExtractorManagerTest {
    
    @ExtractorManager.Extractor
    static class DefaultExtractorController {
    }
    
    @Test
    void getRpcExtractorWithDefaultReturnsDefaultGrpcExtractor() {
        ExtractorManager.Extractor extractor =
            DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractRpcParamExtractor rpc = ExtractorManager.getRpcExtractor(extractor);
        assertNotNull(rpc);
        assertEquals(ExtractorManager.DefaultGrpcExtractor.class, rpc.getClass());
    }
    
    @Test
    void defaultGrpcExtractorExtractParamReturnsEmptyList() throws Exception {
        AbstractRpcParamExtractor extractor = new ExtractorManager.DefaultGrpcExtractor();
        Request request = mock(Request.class);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }
    
    @Test
    void getHttpExtractorWithDefaultReturnsDefaultHttpExtractor() {
        ExtractorManager.Extractor extractor =
            DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        AbstractHttpParamExtractor http = ExtractorManager.getHttpExtractor(extractor);
        assertNotNull(http);
        assertEquals(ExtractorManager.DefaultHttpExtractor.class, http.getClass());
    }
    
    @Test
    void defaultHttpExtractorExtractParamReturnsEmptyList() throws Exception {
        AbstractHttpParamExtractor extractor = new ExtractorManager.DefaultHttpExtractor();
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }
    
    @Test
    void getRpcExtractorWithConfigRequestParamExtractorReturnsExtractor() {
        ExtractorManager.Extractor extractor =
            ParamExtractorTest.Controller.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractRpcParamExtractor rpc = ExtractorManager.getRpcExtractor(extractor);
        assertNotNull(rpc);
        assertEquals(ConfigRequestParamExtractor.class.getSimpleName(),
            rpc.getClass().getSimpleName());
    }
    
    @Test
    void getRpcExtractorCachesDefaultInstanceAcrossCalls() {
        ExtractorManager.Extractor extractor =
            DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractRpcParamExtractor first = ExtractorManager.getRpcExtractor(extractor);
        AbstractRpcParamExtractor second = ExtractorManager.getRpcExtractor(extractor);
        assertSame(first, second);
    }
    
    @Test
    void getHttpExtractorCachesDefaultInstanceAcrossCalls() {
        ExtractorManager.Extractor extractor =
            DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractHttpParamExtractor first = ExtractorManager.getHttpExtractor(extractor);
        AbstractHttpParamExtractor second = ExtractorManager.getHttpExtractor(extractor);
        assertSame(first, second);
    }
    
    @Test
    void concurrentGetRpcExtractorReturnsSameInstanceAndDoesNotCorrupt() throws Exception {
        ExtractorManager.Extractor extractor =
            DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        
        int threadCount = 32;
        int iterationsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        Set<AbstractRpcParamExtractor> observed = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int j = 0; j < iterationsPerThread; j++) {
                            AbstractRpcParamExtractor rpc =
                                ExtractorManager.getRpcExtractor(extractor);
                            assertNotNull(rpc);
                            observed.add(rpc);
                            AbstractHttpParamExtractor http =
                                ExtractorManager.getHttpExtractor(extractor);
                            assertNotNull(http);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            assertTrue(doneGate.await(30, TimeUnit.SECONDS),
                "concurrent workers did not finish in time");
        } finally {
            pool.shutdownNow();
        }
        
        assertNull(failure.get(), "concurrent access should not throw");
        assertEquals(1, observed.size(),
            "all callers must observe the same cached extractor instance");
    }
}
