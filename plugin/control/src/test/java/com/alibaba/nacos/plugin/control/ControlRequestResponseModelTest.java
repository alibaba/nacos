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

package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.spi.ControlPluginProvider;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlRequestResponseModelTest {
    
    @Test
    void testConnectionCheckRequestAccessors() {
        ConnectionCheckRequest request =
            new ConnectionCheckRequest("127.0.0.1", "app", "sdk");
        
        request.setClientIp("127.0.0.2");
        request.setAppName("console");
        request.setSource("grpc");
        request.setLabels(Collections.singletonMap("region", "cn"));
        
        assertEquals("127.0.0.2", request.getClientIp());
        assertEquals("console", request.getAppName());
        assertEquals("grpc", request.getSource());
        assertEquals(Collections.singletonMap("region", "cn"), request.getLabels());
    }
    
    @Test
    void testTpsCheckRequestAccessors() {
        TpsCheckRequest request = new TpsCheckRequest("point", "conn", "127.0.0.1");
        
        request.setPointName("newPoint");
        request.setConnectionId("newConn");
        request.setClientIp("127.0.0.2");
        request.setTimestamp(100L);
        request.setCount(3L);
        
        assertEquals("newPoint", request.getPointName());
        assertEquals("newConn", request.getConnectionId());
        assertEquals("127.0.0.2", request.getClientIp());
        assertEquals(100L, request.getTimestamp());
        assertEquals(3L, request.getCount());
    }
    
    @Test
    void testResponsesAndCodes() {
        TpsCheckResponse tps = new TpsCheckResponse(true, TpsResultCode.PASS_BY_POINT, "pass");
        ConnectionCheckResponse connection = new ConnectionCheckResponse();
        
        tps.setSuccess(false);
        tps.setCode(TpsResultCode.DENY_BY_POINT);
        tps.setMessage("deny");
        connection.setSuccess(true);
        connection.setCode(ConnectionCheckCode.PASS_BY_TOTAL);
        connection.setMessage("ok");
        connection.setLimitMessage("limit");
        
        assertFalse(tps.isSuccess());
        assertEquals(TpsResultCode.DENY_BY_POINT, tps.getCode());
        assertEquals("deny", tps.getMessage());
        assertTrue(connection.isSuccess());
        assertEquals(ConnectionCheckCode.PASS_BY_TOTAL, connection.getCode());
        assertEquals("ok", connection.getMessage());
        assertEquals("limit", connection.getLimitMessage());
        assertEquals(201, TpsResultCode.PASS_BY_MONITOR);
        assertEquals(100, TpsResultCode.CHECK_SKIP);
        assertEquals(205, ConnectionCheckCode.PASS_BY_MONITOR);
        assertEquals(100, ConnectionCheckCode.CHECK_SKIP);
        assertEquals(TpsResultCode.class, new TpsResultCode().getClass());
        assertEquals(ConnectionCheckCode.class, new ConnectionCheckCode().getClass());
    }
    
    @Test
    void testRuleModelValues() {
        assertEquals(RuleModel.FUZZY, RuleModel.valueOf("FUZZY"));
        assertEquals(RuleModel.PROTO, RuleModel.values()[1]);
    }
    
    @Test
    void testRuleChangeEvents() {
        TpsControlRuleChangeEvent tpsEvent = new TpsControlRuleChangeEvent("point", false);
        ConnectionLimitRuleChangeEvent connectionEvent = new ConnectionLimitRuleChangeEvent(false);
        
        tpsEvent.setPointName("newPoint");
        tpsEvent.setExternal(true);
        connectionEvent.setExternal(true);
        
        assertEquals("newPoint", tpsEvent.getPointName());
        assertTrue(tpsEvent.isExternal());
        assertTrue(connectionEvent.isExternal());
    }
    
    @Test
    void testControlPluginProvider() {
        ControlPluginProvider provider = new ControlPluginProvider();
        
        assertEquals("control", provider.getPluginType().getType());
        assertNotNull(provider.getAllPlugins());
    }
}
