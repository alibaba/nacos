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

package com.alibaba.nacos.core.remote.circuitbreaker;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.control.*;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CircuitBreaker.
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class CircuitBreaker {

    public static final String DEFAULT_RULE = "default";

    private String currentRuleName = DEFAULT_RULE;

    private CircuitBreakerStrategy currentRule;

    private ConfigLoader configLoader;

    private final ConcurrentHashMap<String, CircuitBreakerConfig> pointConfigMap = new ConcurrentHashMap<>();

    CircuitBreaker() {
        init();
    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyStrategy(String pointName, String connectionId, List<MonitorKey> monitorKeyList) {

        // Check monitor keys & check total tps.
        return currentRule.applyStrategy(pointName, connectionId, monitorKeyList);
    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name
     * @param  connectionId the specific circuit break strategy that client wants to apply for the current point
     *
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyStrategyForClientIp(String pointName, String connectionId, String clientIp) {
        // Check monitor keys & check total tps.
        return currentRule.applyStrategyForClientIp(pointName, connectionId, clientIp);
    }

    /**
     * Init the current global CircuitBreaker. Load configs from local or DB.
     */
    private void init() {
        pointConfigMap.put(DEFAULT_RULE, new CircuitBreakerConfig());
        configLoader = new ConfigLoader();
        loadConfig();

        ServiceLoader<CircuitBreakerStrategy> circuitBreakers = ServiceLoader.load(CircuitBreakerStrategy.class);
        System.out.println(circuitBreakers);

        // SPI mechanism to load rule implementation as current circuit breaker strategy
        for (CircuitBreakerStrategy circuitBreakerStrategy : circuitBreakers) {
            System.out.println(circuitBreakerStrategy.getRuleName());
            if (circuitBreakerStrategy.getRuleName().equals(DEFAULT_RULE)) {

                this.currentRule = circuitBreakerStrategy.getStrategy();

            }
        }

    }

    /**
     * Register new point.
     * TODO: add register logic
     * @param  pointName entry point name or class name
     */
    public void registerPoint(String pointName) {
        Loggers.TPS_CONTROL
                .info("Register tps control,pointName={} ", pointName);
        this.currentRule.registerPoint(pointName);
    }

    public void applyRule(String pointName, CircuitBreakerConfig config) {
        this.currentRule.applyRule(pointName, config, new HashMap<>());
    }


    /**
     * Load config from local file or remote db.
     */
    public  void loadConfig() {
        configLoader.updateLocalConfig();
    }

    /**
     * Get the current config for a specific point.
     *
     * @param pointName config's point name.
     */
    public  CircuitBreakerConfig getConfig(String pointName) {
        return pointConfigMap.getOrDefault(pointName, new CircuitBreakerConfig());
    }

    private synchronized void loadRuleFromLocal(String pointName) throws IOException {

        File pointFile = getRuleFile(pointName);
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }
        String pointConfigContent = DiskUtils.readFile(pointFile);
        String monitorKeyContent = DiskUtils.readFile(pointFile);

        CircuitBreakerConfig pointConfig = currentRule.deserializePointConfig(pointConfigContent);
        Map<String, CircuitBreakerConfig> monitorKeyConfig = currentRule.deserializeMonitorKeyConfig(monitorKeyContent);

        Loggers.TPS_CONTROL.info("Load rule from local,pointName={}, ruleContent={} ", pointName,
                pointConfigContent);
        Loggers.TPS_CONTROL.info("Load rule from local,pointName={}, monitor keys ruleContent={} ", pointName,
                monitorKeyContent);

        currentRule.applyRule(pointName, pointConfig, monitorKeyConfig);
    }

    private synchronized void saveRuleToLocal(String pointName, CircuitBreakerConfig config,
                                              Map<String, CircuitBreakerConfig> monitorKeyConfig) throws IOException {

        File pointFile = getRuleFile(pointName);
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }

        File monitorKeyFile = getMonitorKeyRuleFile(pointName);
        if (!monitorKeyFile.exists()) {
            monitorKeyFile.createNewFile();
        }
        String content = JacksonUtils.toJson(config);
        String monitorKeyContent = JacksonUtils.toJson(monitorKeyConfig);
        DiskUtils.writeFile(pointFile, content.getBytes(Constants.ENCODE), false);
        DiskUtils.writeFile(monitorKeyFile, monitorKeyContent.getBytes(Constants.ENCODE), false);
        Loggers.TPS_CONTROL.info("Save rule to local,pointName={}, ruleContent ={}, monitorKeysContent ={} ", pointName, content, monitorKeyContent);
    }

    private File getRuleFile(String pointName) {
        File baseDir = checkBaseDir();
        return new File(baseDir, pointName);
    }

    private File getMonitorKeyRuleFile(String pointName) {
        File baseDir = checkBaseDir();
        return new File(baseDir, pointName + "MonitorKeys");
    }

    private File checkBaseDir() {
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }
}