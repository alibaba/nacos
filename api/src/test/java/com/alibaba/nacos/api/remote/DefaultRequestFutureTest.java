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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DefaultRequestFutureTest {
    
    private static final String CONNECTION_ID = "1233_1.1.1.1_3306";
    
    private static final String REQUEST_ID = "10000";
    
    @Mock
    private Response response;
    
    @Mock
    private ExecutorService executor;
    
    private long timestamp;
    
    @Before
    public void setUp() throws Exception {
        timestamp = System.currentTimeMillis();
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testSyncGetResponseSuccessWithoutTimeout() throws InterruptedException {
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
    public void testSyncGetResponseFailureWithoutTimeout() throws InterruptedException {
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
    public void testSyncGetResponseSuccessWithTimeout() throws InterruptedException, TimeoutException {
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
    public void testSyncGetResponseSuccessWithInvalidTimeout() throws InterruptedException, TimeoutException {
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
    
    @Test(expected = TimeoutException.class)
    public void testSyncGetResponseFailureWithTimeout() throws InterruptedException, TimeoutException {
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID);
        requestFuture.get(100L);
    }
    
    @Test
    public void testSyncGetResponseSuccessByTriggerWithoutTimeout() throws InterruptedException {
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
    public void testSyncGetResponseFailureByTriggerWithoutTimeout() throws InterruptedException {
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
    public void testSyncGetResponseSuccessByTriggerWithTimeout() throws InterruptedException, TimeoutException {
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
    
    @Test(expected = TimeoutException.class)
    public void testSyncGetResponseFailureByTriggerWithTimeout() throws InterruptedException, TimeoutException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, null, trigger);
        try {
            requestFuture.get(100L);
        } finally {
            assertTrue(trigger.isTimeout);
        }
    }
    
    @Test
    public void testASyncGetResponseSuccessWithoutTimeout() throws InterruptedException {
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
    public void testASyncGetResponseSuccessWithoutTimeoutByExecutor() throws InterruptedException {
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
    public void testASyncGetResponseFailureWithoutTimeout() throws InterruptedException {
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
    public void testASyncGetResponseFailureWithTimeout() throws InterruptedException {
        MockTimeoutInnerTrigger trigger = new MockTimeoutInnerTrigger();
        MockRequestCallback callback = new MockRequestCallback(100L);
        final DefaultRequestFuture requestFuture = new DefaultRequestFuture(CONNECTION_ID, REQUEST_ID, callback, trigger);
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