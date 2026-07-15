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
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
