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

package com.alibaba.nacos.plugin.control.impl;

import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosConnectionControlManagerTest {
    
    @Test
    void testApplyConnectionLimitRule() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(10);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionControlRule connectionLimitRule = nacosConnectionControlManager.getConnectionLimitRule();
        assertEquals(connectionControlRule, connectionLimitRule);
    }
    
    @Test
    void testCheckLimit() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(10);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "test");
        ConnectionCheckResponse connectionCheckResponse = nacosConnectionControlManager.check(connectionCheckRequest);
        assertFalse(connectionCheckResponse.isSuccess());
    }
    
    @Test
    void testCheckUnLimit() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "test");
        ConnectionCheckResponse connectionCheckResponse = nacosConnectionControlManager.check(connectionCheckRequest);
        assertTrue(connectionCheckResponse.isSuccess());
    }
    
    @Test
    void testCheckLimitCountLessThanZero() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(-1);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "test");
        ConnectionCheckResponse connectionCheckResponse = nacosConnectionControlManager.check(connectionCheckRequest);
        assertTrue(connectionCheckResponse.isSuccess());
    }
}
