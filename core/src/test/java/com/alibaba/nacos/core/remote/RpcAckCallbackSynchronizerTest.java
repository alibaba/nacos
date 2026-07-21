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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.DefaultRequestFuture;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcAckCallbackSynchronizerTest {
    
    private static final String CONN_ID = "conn-" + System.currentTimeMillis();
    
    @AfterEach
    void tearDown() {
        RpcAckCallbackSynchronizer.clearContext(CONN_ID);
        RpcAckCallbackSynchronizer.CALLBACK_CONTEXT.clear();
    }
    
    @Test
    void testAckNotifyWhenConnectionContextNull() {
        Response resp = new HealthCheckResponse();
        resp.setRequestId("req1");
        RpcAckCallbackSynchronizer.ackNotify("nonexistent-conn", resp);
    }
    
    @Test
    void testAckNotifyWhenRequestIdNotInContext() throws NacosException {
        RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        Response resp = new HealthCheckResponse();
        resp.setRequestId("req-absent");
        RpcAckCallbackSynchronizer.ackNotify(CONN_ID, resp);
    }
    
    @Test
    void testAckNotifySuccess() throws Exception {
        DefaultRequestFuture future = new DefaultRequestFuture(CONN_ID, "req1");
        RpcAckCallbackSynchronizer.syncCallback(CONN_ID, "req1", future);
        HealthCheckResponse response = new HealthCheckResponse();
        response.setRequestId("req1");
        RpcAckCallbackSynchronizer.ackNotify(CONN_ID, response);
        Response got = future.get(1000L);
        assertNotNull(got);
        assertTrue(got.isSuccess());
    }
    
    @Test
    void testAckNotifyFail() throws Exception {
        DefaultRequestFuture future = new DefaultRequestFuture(CONN_ID, "req2");
        RpcAckCallbackSynchronizer.syncCallback(CONN_ID, "req2", future);
        HealthCheckResponse response = new HealthCheckResponse();
        response.setRequestId("req2");
        response.setErrorInfo(500, "error");
        RpcAckCallbackSynchronizer.ackNotify(CONN_ID, response);
        Response got = future.get(1000L);
        assertNull(got);
    }
    
    @Test
    void testSyncCallbackRequestIdConflict() throws NacosException {
        RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        DefaultRequestFuture f1 = new DefaultRequestFuture(CONN_ID, "reqConflict");
        DefaultRequestFuture f2 = new DefaultRequestFuture(CONN_ID, "reqConflict");
        RpcAckCallbackSynchronizer.syncCallback(CONN_ID, "reqConflict", f1);
        NacosException ex = assertThrows(NacosException.class,
            () -> RpcAckCallbackSynchronizer.syncCallback(CONN_ID, "reqConflict", f2));
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("request id conflict"));
    }
    
    @Test
    void testInitContextIfNecessaryExistingKey() throws NacosException {
        Map<String, DefaultRequestFuture> first =
            RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        Map<String, DefaultRequestFuture> second =
            RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        assertTrue(first == second);
    }
    
    @Test
    void testClearContext() throws NacosException {
        RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        RpcAckCallbackSynchronizer.clearContext(CONN_ID);
        Map<String, DefaultRequestFuture> after =
            RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        assertNotNull(after);
    }
    
    @Test
    void testClearFutureWhenConnectionAbsent() {
        RpcAckCallbackSynchronizer.clearFuture("absent-conn", "req1");
    }
    
    @Test
    void testClearFutureWhenRequestIdAbsent() throws NacosException {
        RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        RpcAckCallbackSynchronizer.clearFuture(CONN_ID, "absent-req");
    }
    
    @Test
    void testClearFutureRemovesRequestId() throws NacosException {
        RpcAckCallbackSynchronizer.syncCallback(CONN_ID, "reqToClear",
            new DefaultRequestFuture(CONN_ID, "reqToClear"));
        Map<String, DefaultRequestFuture> ctx =
            RpcAckCallbackSynchronizer.initContextIfNecessary(CONN_ID);
        assertTrue(ctx.containsKey("reqToClear"));
        RpcAckCallbackSynchronizer.clearFuture(CONN_ID, "reqToClear");
        assertFalse(ctx.containsKey("reqToClear"));
    }
    
    @Test
    void testTrimCallbackContextReturnsToLimitAndFailsPendingFuture() throws Exception {
        DefaultRequestFuture future = new DefaultRequestFuture("conn-trim", "req-trim");
        RpcAckCallbackSynchronizer.syncCallback("conn-trim", "req-trim", future);
        
        RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(0);
        
        assertEquals(0, RpcAckCallbackSynchronizer.CALLBACK_CONTEXT.size());
        assertTrue(future.isDone());
        assertNull(future.get(1L));
    }
    
    @Test
    void testTrimCallbackContextUsesCapacityReason() throws NacosException {
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        DefaultRequestFuture future = new DefaultRequestFuture("conn-reason", "req-reason",
            new CapturingRequestCallBack(throwableRef), null);
        RpcAckCallbackSynchronizer.syncCallback("conn-reason", "req-reason", future);
        
        RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(0);
        
        assertNotNull(throwableRef.get());
        assertTrue(throwableRef.get() instanceof TimeoutException);
        assertTrue(
            throwableRef.get().getMessage().contains("RPC_ACK_CALLBACK_CONTEXT_CAPACITY_EXCEEDED"));
        assertTrue(throwableRef.get().getMessage().contains("connectionId=conn-reason"));
        assertTrue(throwableRef.get().getMessage().contains("requestId=req-reason"));
    }
    
    @Test
    void testTrimCallbackContextContinuesWhenCallbackThrows() throws NacosException {
        AtomicInteger notifiedCount = new AtomicInteger();
        RpcAckCallbackSynchronizer.syncCallback("conn-callback", "req-throw",
            new DefaultRequestFuture("conn-callback", "req-throw", new ThrowingRequestCallBack(),
                null));
        RpcAckCallbackSynchronizer.syncCallback("conn-callback", "req-ok",
            new DefaultRequestFuture("conn-callback", "req-ok",
                new CountingRequestCallBack(notifiedCount), null));
        
        RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(0);
        
        assertEquals(1, notifiedCount.get());
    }
    
    @Test
    void testConcurrentRegistrationAndTrimDoesNotLeaveDetachedFuture() throws Exception {
        int taskCount = 32;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<DefaultRequestFuture> futures = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            String connectionId = "conn-race-" + i;
            String requestId = "req-race-" + i;
            DefaultRequestFuture future = new DefaultRequestFuture(connectionId, requestId);
            futures.add(future);
            executorService.submit(() -> {
                await(startLatch);
                try {
                    RpcAckCallbackSynchronizer.syncCallback(connectionId, requestId, future);
                } catch (NacosException ignored) {
                }
            });
        }
        executorService.submit(() -> {
            await(startLatch);
            for (int i = 0; i < taskCount; i++) {
                RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(4);
            }
        });
        
        startLatch.countDown();
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(4);
        
        for (int i = 0; i < taskCount; i++) {
            String connectionId = "conn-race-" + i;
            String requestId = "req-race-" + i;
            Map<String, DefaultRequestFuture> context =
                RpcAckCallbackSynchronizer.CALLBACK_CONTEXT.get(connectionId);
            assertTrue(futures.get(i).isDone()
                || context != null && context.get(requestId) == futures.get(i));
        }
    }
    
    @Test
    void testAckRacingWithTrimCompletesFutureAtMostOnce() throws Exception {
        AtomicInteger completionCount = new AtomicInteger();
        DefaultRequestFuture future = new DefaultRequestFuture("conn-ack-race", "req-ack-race",
            new CountingRequestCallBack(completionCount), null);
        RpcAckCallbackSynchronizer.syncCallback("conn-ack-race", "req-ack-race", future);
        RpcAckCallbackSynchronizer.initContextIfNecessary("conn-ack-race-keep");
        Response response = new HealthCheckResponse();
        response.setRequestId("req-ack-race");
        
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        executorService.submit(() -> {
            await(startLatch);
            RpcAckCallbackSynchronizer.ackNotify("conn-ack-race", response);
        });
        executorService.submit(() -> {
            await(startLatch);
            RpcAckCallbackSynchronizer.trimCallbackContextIfNecessary(1);
        });
        
        startLatch.countDown();
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        
        assertTrue(completionCount.get() <= 1);
    }
    
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static class CapturingRequestCallBack implements RequestCallBack<Response> {
        
        private final AtomicReference<Throwable> throwableRef;
        
        CapturingRequestCallBack(AtomicReference<Throwable> throwableRef) {
            this.throwableRef = throwableRef;
        }
        
        @Override
        public Executor getExecutor() {
            return null;
        }
        
        @Override
        public long getTimeout() {
            return TimeUnit.MINUTES.toMillis(1);
        }
        
        @Override
        public void onResponse(Response response) {
        }
        
        @Override
        public void onException(Throwable e) {
            throwableRef.set(e);
        }
    }
    
    private static class CountingRequestCallBack extends CapturingRequestCallBack {
        
        private final AtomicInteger count;
        
        CountingRequestCallBack(AtomicInteger count) {
            super(new AtomicReference<>());
            this.count = count;
        }
        
        @Override
        public void onResponse(Response response) {
            count.incrementAndGet();
        }
        
        @Override
        public void onException(Throwable e) {
            count.incrementAndGet();
        }
    }
    
    private static class ThrowingRequestCallBack extends CapturingRequestCallBack {
        
        ThrowingRequestCallBack() {
            super(new AtomicReference<>());
        }
        
        @Override
        public void onException(Throwable e) {
            throw new IllegalStateException("callback failure");
        }
    }
}
