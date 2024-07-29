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

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.rule.parser.ConnectionControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.parser.NacosConnectionControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * connection control manager.
 *
 * @author shiyiyu
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class ConnectionControlManager {
    
    private final ConnectionControlRuleParser connectionControlRuleParser;
    
    protected ConnectionControlRule connectionControlRule;
    
    protected Collection<ConnectionMetricsCollector> metricsCollectorList;
    
    private ScheduledExecutorService executorService;
    
    public ConnectionControlManager() {
        metricsCollectorList = NacosServiceLoader.load(ConnectionMetricsCollector.class);
        Loggers.CONTROL.info("Load connection metrics collector,size={},{}", metricsCollectorList.size(),
                metricsCollectorList);
        this.connectionControlRuleParser = buildConnectionControlRuleParser();
        initConnectionRule();
        if (!metricsCollectorList.isEmpty()) {
            initExecuteService();
            startConnectionMetricsReport();
        }
    }
    
    /**
     * get manager name.
     *
     * @return name
     */
    public abstract String getName();
    
    protected ConnectionControlRuleParser buildConnectionControlRuleParser() {
        return new NacosConnectionControlRuleParser();
    }
    
    public ConnectionControlRuleParser getConnectionControlRuleParser() {
        return connectionControlRuleParser;
    }
    
    private void initExecuteService() {
        executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
            Thread thread = new Thread(r, "nacos.plugin.control.connection.reporter");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    private void initConnectionRule() {
        RuleStorageProxy ruleStorageProxy = RuleStorageProxy.getInstance();
        String localRuleContent = ruleStorageProxy.getLocalDiskStorage().getConnectionRule();
        if (StringUtils.isNotBlank(localRuleContent)) {
            Loggers.CONTROL.info("Found local disk connection rule content on start up,value  ={}", localRuleContent);
        } else if (ruleStorageProxy.getExternalStorage() != null
                && ruleStorageProxy.getExternalStorage().getConnectionRule() != null) {
            localRuleContent = ruleStorageProxy.getExternalStorage().getConnectionRule();
            if (StringUtils.isNotBlank(localRuleContent)) {
                Loggers.CONTROL.info("Found persist disk connection rule content on start up ,value  ={}",
                        localRuleContent);
            }
        }
        
        if (StringUtils.isNotBlank(localRuleContent)) {
            connectionControlRule = connectionControlRuleParser.parseRule(localRuleContent);
            Loggers.CONTROL.info("init connection rule end");
            
        } else {
            Loggers.CONTROL.info("No connection rule content found ,use default empty rule ");
            connectionControlRule = connectionControlRuleParser.parseRule("");
        }
    }
    
    private void startConnectionMetricsReport() {
        executorService.scheduleWithFixedDelay(new ConnectionMetricsReporter(), 0, 3000, TimeUnit.MILLISECONDS);
    }
    
    public ConnectionControlRule getConnectionLimitRule() {
        return connectionControlRule;
    }
    
    /**
     * apply connection rule.
     *
     * @param connectionControlRule not null.
     */
    public abstract void applyConnectionLimitRule(ConnectionControlRule connectionControlRule);
    
    /**
     * check connection allowed.
     *
     * @param connectionCheckRequest connectionCheckRequest.
     * @return connection check response
     */
    public abstract ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest);
    
    class ConnectionMetricsReporter implements Runnable {
        
        @Override
        public void run() {
            Map<String, Integer> metricsTotalCount = metricsCollectorList.stream().collect(
                    Collectors.toMap(ConnectionMetricsCollector::getName, ConnectionMetricsCollector::getTotalCount));
            int totalCount = metricsTotalCount.values().stream().mapToInt(Integer::intValue).sum();
            
            Loggers.CONNECTION.info("ConnectionMetrics, totalCount = {}, detail = {}", totalCount, metricsTotalCount);
        }
    }
}
