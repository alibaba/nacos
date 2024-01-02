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
import org.junit.Assert;
import org.junit.Test;

public class NacosConnectionControlManagerTest {
    
    @Test
    public void testApplyConnectionLimitRule() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(10);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionControlRule connectionLimitRule = nacosConnectionControlManager.getConnectionLimitRule();
        Assert.assertEquals(connectionControlRule, connectionLimitRule);
    }
    
    @Test
    public void testCheckLimit() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(10);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "test");
        ConnectionCheckResponse connectionCheckResponse = nacosConnectionControlManager.check(connectionCheckRequest);
        Assert.assertFalse(connectionCheckResponse.isSuccess());
    }
    
    @Test
    public void testCheckUnLimit() {
        NacosConnectionControlManager nacosConnectionControlManager = new NacosConnectionControlManager();
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        nacosConnectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "test");
        ConnectionCheckResponse connectionCheckResponse = nacosConnectionControlManager.check(connectionCheckRequest);
        Assert.assertTrue(connectionCheckResponse.isSuccess());
    }
}
