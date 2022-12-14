/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import org.junit.Assert;
import org.junit.Test;

/**
 * two fixed metrics, total 30, iptotal 15, detail is testa(total-20,iptotal-10),testb(total-10,iptotal-5).
 */
public class NacosConnectionControlManagerTest {
    
    NacosConnectionControlManager connectionControlManager = new NacosConnectionControlManager();
    
    @Test
    public void test() {
        
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        connectionControlRule.setCountLimit(40);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        check = connectionControlManager.check(connectionCheckRequest);
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_SKIP, check.getCode());
        
    }
    
}
