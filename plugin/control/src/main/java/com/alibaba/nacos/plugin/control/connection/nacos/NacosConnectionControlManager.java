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

package com.alibaba.nacos.plugin.control.connection.nacos;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.ConnectionMetricsCollector;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * connection control manager.
 *
 * @author shiyiyue
 */
public class NacosConnectionControlManager extends ConnectionControlManager {
    
    @Override
    public String getName() {
        return "nacos";
    }
    
    public NacosConnectionControlManager() {
        super();
    }
    
    @Override
    public void applyConnectionLimitRule(ConnectionControlRule connectionControlRule) {
        super.connectionControlRule = connectionControlRule;
        Loggers.CONTROL.info("Connection control rule updated to ->" + (this.connectionControlRule == null ? null
                : JacksonUtils.toJson(this.connectionControlRule)));
        
    }
    
    @Override
    public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
        
        ConnectionCheckResponse connectionCheckResponse = new ConnectionCheckResponse();
        //limit rule check.
        if (this.connectionControlRule != null) {
            
            int totalCountLimit = connectionControlRule.getCountLimit();
            Map<String, Integer> metricsTotalCount = metricsCollectorList.stream().collect(
                    Collectors.toMap(ConnectionMetricsCollector::getName, ConnectionMetricsCollector::getTotalCount));
            int totalCount = metricsTotalCount.values().stream().mapToInt(Integer::intValue).sum();
            //total count check model
            if (totalCountLimit >= 0 && totalCount >= totalCountLimit) {
                //deny;
                connectionCheckResponse.setCode(ConnectionCheckCode.DENY_BY_TOTAL_OVER);
                connectionCheckResponse.setMessage(
                        "total count over limit,max allowed count is " + totalCountLimit + ",current count detail is "
                                + metricsTotalCount.toString());
                Loggers.CONNECTION.warn("total count over limit,max allowed count is {},current count detail is {}"
                                + ",clientIp={},appName={},source={},labels={}", totalCountLimit,
                        metricsTotalCount.toString(), connectionCheckRequest.getClientIp(),
                        connectionCheckRequest.getAppName(), connectionCheckRequest.getSource(),
                        connectionCheckRequest.getLabels());
                return connectionCheckResponse;
            }
            
            connectionCheckResponse.setSuccess(true);
            connectionCheckResponse.setCode(ConnectionCheckCode.PASS_BY_TOTAL);
            connectionCheckResponse.setMessage("check pass");
            return connectionCheckResponse;
        } else {
            connectionCheckResponse.setCode(ConnectionCheckCode.CHECK_SKIP);
            connectionCheckResponse.setSuccess(true);
            return connectionCheckResponse;
        }
        
    }
    
}
