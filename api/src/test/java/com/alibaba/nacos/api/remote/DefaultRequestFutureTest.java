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

package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultRequestFutureTest {
    
    private static final String CONNECTION_ID = "1233_1.1.1.1_3306";
    
    private static final String REQUEST_ID = "10000";
    
    @Mock
    private Response response;
    
    @Mock
    private ExecutorService executor;
    
    private long timestamp;
    
    @BeforeEach
    void setUp() throws Exception {
        timestamp = System.currentTimeMillis();
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testSyncGetResponseSuccessWithoutTimeout() throws InterruptedException {
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get();
        assertEquals(response, actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
    }
    
    @Test
    void testSyncGetResponseFailureWithoutTimeout() throws InterruptedException {
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setFailResult(new RuntimeException("test"));
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get();
        assertNull(actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
    }
    
    @Test
    void testSyncGetResponseSuccessWithTimeout() throws InterruptedException, TimeoutException {
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get(1000L);
        assertEquals(response, actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
    }
    
    @Test
    void testSyncGetResponseSuccessWithInvalidTimeout() throws InterruptedException, TimeoutException {
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get(-1L);
        assertEquals(response, actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
    }
    
    @Test
    void testSyncGetResponseFailureWithTimeout() throws InterruptedException, TimeoutException {
        assertThrows(TimeoutException.class, () -> {
            DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
            requestFuture.get(100L);
        });
    }
    
    @Test
    void testSyncGetResponseSuccessByTriggerWithoutTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, null, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get();
        assertEquals(response, actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
        assertFalse(trigger.isTimeout);
    }
    
    @Test
    void testSyncGetResponseFailureByTriggerWithoutTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, null, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setFailResult(new RuntimeException("test"));
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get();
        assertNull(actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
        assertFalse(trigger.isTimeout);
    }
    
    @Test
    void testSyncGetResponseSuccessByTriggerWithTimeout() throws InterruptedException, TimeoutException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, null, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        Response actual = requestFuture.get(1000L);
        assertEquals(response, actual);
        assertEquals(CONNECTION_ID, requestFuture.getConnectionId());
        assertEquals(REQUEST_ID, requestFuture.getRequestId());
        assertTrue(requestFuture.isDone());
        assertTrue(requestFuture.getTimeStamp() >= timestamp);
        assertFalse(trigger.isTimeout);
    }
    
    @Test
    void testSyncGetResponseFailureByTriggerWithTimeout() throws InterruptedException, TimeoutException {
        assertThrows(TimeoutException.class, () -> {
            MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
            DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, null, trigger);
            try {
                requestFuture.get(100L);
            } finally {
                assertTrue(trigger.isTimeout);
            }
        });
    }
    
    @Test
    void testASyncGetResponseSuccessWithoutTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        MockRequestCallback callback = new MockRequestCallback(200L);
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, callback, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        TimeUnit.MILLISECONDS.sleep(250);
        assertEquals(response, callback.response);
        assertNull(callback.exception);
        assertFalse(trigger.isTimeout);
        assertEquals(callback, requestFuture.getRequestCallBack());
    }
    
    @Test
    void testASyncGetResponseSuccessWithoutTimeoutByExecutor() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        MockRequestCallback callback = new MockRequestCallback(executor, 200L);
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, callback, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setResponse(response);
            } catch (Exception ignored) {
            }
        }).start();
        TimeUnit.MILLISECONDS.sleep(250);
        assertEquals(callback, requestFuture.getRequestCallBack());
        verify(executor).execute(any(DefaultRequestFuture.CallBackHandler.class));
    }
    
    @Test
    void testASyncGetResponseFailureWithoutTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        MockRequestCallback callback = new MockRequestCallback(1000L);
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, callback, trigger);
        new Thread(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
                requestFuture.setFailResult(new RuntimeException("test"));
            } catch (Exception ignored) {
            }
        }).start();
        TimeUnit.MILLISECONDS.sleep(250);
        assertNull(callback.response);
        assertTrue(callback.exception instanceof RuntimeException);
        assertFalse(trigger.isTimeout);
        assertEquals(callback, requestFuture.getRequestCallBack());
    }
    
    @Test
    void testASyncGetResponseFailureWithTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        MockRequestCallback callback = new MockRequestCallback(100L);
        final DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, callback,
                trigger);
        TimeUnit.MILLISECONDS.sleep(500);
        assertNull(callback.response);
        assertTrue(callback.exception instanceof TimeoutException);
        assertTrue(trigger.isTimeout);
        assertEquals(callback, requestFuture.getRequestCallBack());
    }
    
    private class MockTimeoutInnerTrigger implements DefaultRequestFuture.TimeoutInnerTrigger {
        
        boolean isTimeout;
        
        @Override
        public void triggerOnTimeout() {
            isTimeout = true;
        }
    }
    
    private class MockRequestCallback implements RequestCallBack<Response> {
        
        private final Executor executor;
        
        private final long timeout;
        
        private Response response;
        
        private Throwable exception;
        
        public MockRequestCallback(long timeout) {
            this(null, timeout);
        }
        
        public MockRequestCallback(Executor executor, long timeout) {
            this.executor = executor;
            this.timeout = timeout;
        }
        
        @Override
        public Executor getExecutor() {
            return executor;
        }
        
        @Override
        public long getTimeout() {
            return timeout;
        }
        
        @Override
        public void onResponse(Response response) {
            this.response = response;
        }
        
        @Override
        public void onException(Throwable e) {
            exception = e;
        }
    }
}