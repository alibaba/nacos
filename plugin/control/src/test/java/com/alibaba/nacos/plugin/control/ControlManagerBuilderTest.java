/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.spi.ControlManagerBuilder;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.Map;

public class ControlManagerBuilderTest implements ControlManagerBuilder {
    
    @Override
    public String getName() {
        return "test";
    }
    
    @Override
    public ConnectionControlManager buildConnectionControlManager() {
        return new ConnectionControlManager() {
            
            @Override
            public String getName() {
                return "testConnection";
            }
            
            @Override
            public void applyConnectionLimitRule(ConnectionControlRule connectionControlRule) {
            
            }
            
            @Override
            public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
                ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
                connectionCheckResponse.setSuccess(true);
                connectionCheckResponse.setCode(ConnectionCheckCode.PASS_BY_TOTAL);
                return connectionCheckResponse;
            }
        };
    }
    
    @Override
    public TpsControlManager buildTpsControlManager() {
        return new TpsControlManager() {
            
            @Override
            public void registerTpsPoint(String pointName) {
            
            }
            
            @Override
            public Map<String, TpsBarrier> getPoints() {
                return null;
            }
            
            @Override
            public Map<String, TpsControlRule> getRules() {
                return null;
            }
            
            @Override
            public void applyTpsRule(String pointName, TpsControlRule rule) {
            
            }
            
            @Override
            public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
                return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
            }
            
            @Override
            public String getName() {
                return "testTps";
            }
        };
    }
}
