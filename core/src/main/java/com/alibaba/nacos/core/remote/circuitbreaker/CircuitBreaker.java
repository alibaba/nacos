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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.control.*;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CircuitBreaker.
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
@Service
public class CircuitBreaker {

    public static final String DEFAULT_RULE = "default";

    private String currentRuleName = DEFAULT_RULE;

    private CircuitBreakerStrategy currentRule;

    private ConfigLoader configLoader;

    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.core.remote.circuitbreaker.reporter");
        thread.setDaemon(true);
        return thread;
    });

    public CircuitBreaker() {
        init();
    }

    public CircuitBreaker(String strategy) {
        currentRuleName = strategy;
        init();
    }

    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyStrategy(String pointName, List<MonitorKey> monitorKeyList) {

        // Check monitor keys & check total tps.
        return currentRule.applyStrategy(pointName, monitorKeyList);
    }


    /**
     * Main entry point for circuit breaker rule implementations.
     * Using Java SPI to load circuit break rule and apply for their rules.
     *
     * @param  pointName entry point name or class name (TODO: can be modified through Nacos console)
     * @return true when the current request is allowed to continue; false if the request breaks the upper limit
     */
    public boolean applyStrategyWithLoad(String pointName, List<MonitorKey> monitorKeyList, long load) {

        // Check monitor keys & check total tps.
        return currentRule.applyStrategyWithLoad(pointName, monitorKeyList, load);
    }

    /**
     * Init the current global CircuitBreaker. Load configs from local or DB.
     */
    private void init() {
        configLoader = new ConfigLoader();
        loadConfig();
        registerFileWatch();

        ServiceLoader<CircuitBreakerStrategy> circuitBreakers = ServiceLoader.load(CircuitBreakerStrategy.class);
        System.out.println(circuitBreakers);

        // SPI mechanism to load rule implementation as current circuit breaker strategy
        for (CircuitBreakerStrategy circuitBreakerStrategy : circuitBreakers) {
            System.out.println(circuitBreakerStrategy.getRuleName());
            if (circuitBreakerStrategy.getRuleName().equals(currentRuleName)) {
                this.currentRule = circuitBreakerStrategy.getStrategy();
            }
        }
        executorService.scheduleWithFixedDelay(new CircuitBreakerReporter(), 0, 900, TimeUnit.MILLISECONDS);
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

    public void applyRule(String pointName, CircuitBreakerConfig config, Map<String, CircuitBreakerConfig> monitorKeysMap) {
        this.currentRule.applyRule(pointName, config, monitorKeysMap);
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
    public CircuitBreakerConfig getConfig(String pointName) {
        return null;
    }

    private synchronized void loadRuleFromLocal(String pointName) throws IOException {

        File pointFile = getRuleFile(pointName);
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }

        File monitorKeyFile = getMonitorKeyRuleFile(pointName);
        if (!monitorKeyFile.exists()) {
            monitorKeyFile.createNewFile();
        }
        String pointConfigContent = DiskUtils.readFile(pointFile);
        String monitorKeyContent = DiskUtils.readFile(monitorKeyFile);

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

    private void registerFileWatch() {
        try {
            String tpsPath = Paths.get(EnvUtil.getNacosHome(), "data" + File.separator + "cb" + File.separator)
                    .toString();
            System.out.println(tpsPath);
            checkBaseDir();
            WatchFileCenter.registerWatcher(tpsPath, new FileWatcher() {
                @Override
                public void onChange(FileChangeEvent event) {
                    String fileName = event.getContext().toString();
                    try {
                        loadRuleFromLocal(fileName);

                    } catch (Throwable throwable) {
                        Loggers.TPS_CONTROL
                                .warn("Fail to load rule from local,pointName={},error={}", fileName, throwable);
                    }
                }

                @Override
                public boolean interest(String context) {
                    for (String pointName : getAllPointName()) {
                        if (context.startsWith(pointName)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        } catch (NacosException e) {
            Loggers.TPS_CONTROL.warn("Register fire watch fail.", e);
        }
    }

    private List<String> getAllPointName() {
        return currentRule.getAllPointName();
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
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "cb" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }

    public static class ReportTime {
        public long now = 0L;

        public long lastReportMinutes = 0L;

        public long lastReportSecond = 0L;

        public long tempMinutes = 0L;

        public long tempSecond = 0L;

        ReportTime(long now, long lastReportSecond, long lastReportMinutes, long tempMinutes, long tempSecond) {
            this.now = now;
            this.lastReportSecond = lastReportSecond;
            this.lastReportMinutes = lastReportMinutes;
            this.tempMinutes = tempMinutes;
            this.tempSecond = tempSecond;
        }
    }

    class CircuitBreakerReporter implements Runnable {

        long lastReportSecond = 0L;

        long lastReportMinutes = 0L;

        @Override
        public void run() {
            Loggers.TPS_CONTROL_DIGEST.info("Circuit Breaker Starts...\n");
            try {
                long now = System.currentTimeMillis();
                ReportTime reportTime = new ReportTime(now, lastReportSecond, lastReportMinutes, 0, 0);
                Map<String, CircuitBreakerMonitor> monitorMap = currentRule.getPointToMonitorMap();

                StringBuilder stringBuilder = new StringBuilder();
                for (Map.Entry<String, CircuitBreakerMonitor> entry : monitorMap.entrySet()) {
                    CircuitBreakerRecorder pointRecorder = entry.getValue().getPointRecorder();
                    stringBuilder.append(currentRule.reportMonitorPoint(reportTime, pointRecorder));

                    Map<String, CircuitBreakerRecorder> monitorKeyMap = entry.getValue().getMonitorKeysRecorder();
                    for (Map.Entry<String, CircuitBreakerRecorder> monitorKeyEntry : monitorKeyMap.entrySet()) {
                        stringBuilder.append(currentRule.reportMonitorKeys(reportTime, monitorKeyEntry.getKey(), monitorKeyEntry.getValue()));
                    }
                }

                if (reportTime.tempSecond > 0) {
                    lastReportSecond = reportTime.tempSecond;
                }
                if (reportTime.tempMinutes > 0) {
                    lastReportMinutes = reportTime.tempMinutes;
                }
                if (stringBuilder.length() > 0) {
                    Loggers.TPS_CONTROL_DIGEST.info("Circuit Breaker reporting...\n" + stringBuilder.toString());
                }
            } catch (Throwable throwable) {
                Loggers.TPS_CONTROL_DIGEST.error("Circuit Breaker reporting error", throwable);

            }

        }
    }

    public CircuitBreakerStrategy getCurrentRule() {
        return this.currentRule;
    }
}