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

package com.alibaba.nacos.core.remote.circuitbreaker.rules.impl;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.*;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.TpsMonitorPoint;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.MapUtils;


import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default rule for circuit breaker.
 * @author czf
 * @version $Id: MatchMode.java, v 0.1 2021年08月08日 12:38 PM chuzefang Exp $
 */
public class TpsDefaultStrategy extends CircuitBreakerStrategy {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final Map<String, TpsMonitor> pointToMonitorMap = new ConcurrentHashMap<>();

    @Override
    public String getRuleName() {
        return "default";
    }

    @Override
    public void registerPoint(String pointName) {
        pointToMonitorMap.putIfAbsent(pointName, new TpsMonitor(pointName));
    }

    /**
     //     * Check for tps condition for the current point.
     //     * TODO: implement this method
     //     */
    @Override
    public boolean applyStrategy(String pointName, List<MonitorKey> monitorKeyList) {
        if (pointToMonitorMap.containsKey(pointName)) {
            TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
            return pointMonitor.applyTps(monitorKeyList);
        }
        return true;
    }

    @Override
    public TpsDefaultStrategy getStrategy() {
        return this;
    }

    @Override
    public void applyRule(String pointName, CircuitBreakerConfig config,
                                     Map<String, CircuitBreakerConfig> keyConfigMap) {

        TpsConfig castedPointConfig = (TpsConfig) config;

        // implicit cast from CircuitBreakerConfig (parent class) to TpsConfig subclass
        Map<String, TpsConfig> castedKeyConfigMap = keyConfigMap.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (TpsConfig) e.getValue()));

        if (pointToMonitorMap.containsKey(pointName)) {
            TpsMonitor pointMonitor = pointToMonitorMap.get(pointName);
            pointMonitor.applyRule(false, castedPointConfig, castedKeyConfigMap);
        } else {
            TpsMonitor newMonitor = new TpsMonitor(pointName, castedPointConfig);
            newMonitor.applyRule(false, castedPointConfig, castedKeyConfigMap);
        }

    }

    @Override
    public Map<String, CircuitBreakerConfig> getAllConfig() {
        return null;
    }

    @Override
    public List<String> getAllPointName() {
        return new ArrayList<>(pointToMonitorMap.keySet());
    }

    @Override
    public Map<String, CircuitBreakerMonitor> getPointRecorders() {
        return null;
    }

    @Override
    public CircuitBreakerConfig deserializePointConfig(String content) {
        return StringUtils.isBlank(content) ? new TpsConfig()
                : JacksonUtils.toObj(content, TpsConfig.class);
    }

    @Override
    public Map<String, CircuitBreakerConfig> deserializeMonitorKeyConfig(String content) {
        TypeReference<Map<String,TpsConfig>> typeRef
                = new TypeReference<Map<String, TpsConfig>>() {};
        Map<String,TpsConfig> configMap = StringUtils.isBlank(content) ? new HashMap<>()
                : JacksonUtils.toObj(content, typeRef);

        Map<String,CircuitBreakerConfig> retMap = new HashMap<>();
        if (MapUtils.isNotEmpty(configMap)) {
            retMap = configMap.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return retMap;
    }

    @Override
    public String reportMonitorPoint(CircuitBreaker.ReportTime reportTime,  CircuitBreakerRecorder pointRecorder) {
        TpsRecorder tpsPoint = (TpsRecorder) pointRecorder;
        StringBuilder stringBuilder = new StringBuilder();

        //get last second
        CircuitBreakerRecorder.Slot pointSlot = pointRecorder.getPoint(reportTime.now - 1000L);
        if (pointSlot == null) {
            return "";
        }

        //already reported.
        if (reportTime.lastReportSecond != 0L && reportTime.lastReportSecond == pointSlot.time) {
            return "";
        }
        String point = pointRecorder.getPointName();
        String formatString = new SimpleDateFormat(DATETIME_PATTERN).format(new Date(reportTime.now - 1000L));
        reportTime.tempSecond = pointSlot.time;
        TpsConfig conf = tpsPoint.getConfig();
        stringBuilder.append(point).append('|').append("point|").append(conf.getPeriod())
                .append('|').append(formatString).append('|')
                .append(pointSlot.getCountHolder(point).count.get()).append('|')
                .append(pointSlot.getCountHolder(point).interceptedCount.get()).append('\n');
        return stringBuilder.toString();
    }

    @Override
    public String reportMonitorKeys(CircuitBreaker.ReportTime reportTime,
                                    String monitorKey, CircuitBreakerRecorder pointRecorder) {
        long lastReportSecond = reportTime.lastReportSecond;
        long lastReportMinutes = reportTime.lastReportMinutes;
        long now = reportTime.now;
        TpsRecorder ipRecord = (TpsRecorder) pointRecorder;
        String point = pointRecorder.getPointName();

        StringBuilder stringBuilder = new StringBuilder();
        TpsConfig conf = ipRecord.getConfig();
        CircuitBreakerRecorder.Slot keySlot = ipRecord.getPoint(now - conf.getPeriod().toMillis(1));
        if (keySlot == null) {
            return "";
        }
        //already reported.
        if (conf.getPeriod() == TimeUnit.SECONDS) {
            if (lastReportSecond != 0L && lastReportSecond == keySlot.time) {
                return "";
            }
        }
        if (conf.getPeriod() == TimeUnit.MINUTES) {
            if (lastReportMinutes != 0L && lastReportMinutes == keySlot.time) {
                return "";
            }
        }

        String timeFormatOfSecond = TpsMonitorPoint.getTimeFormatOfSecond(keySlot.time);
        reportTime.tempMinutes = keySlot.time;
        if (ipRecord.isProtoModel()) {
            Map<String, TpsRecorder.SlotCountHolder> keySlots = ((TpsRecorder.MultiKeyTpsSlot) keySlot).keySlots;
            for (Map.Entry<String, TpsRecorder.SlotCountHolder> slotCountHolder : keySlots.entrySet()) {
                stringBuilder.append(point).append('|').append(monitorKey).append('|')
                        .append(conf.getPeriod()).append('|').append(timeFormatOfSecond).append('|')
                        .append(slotCountHolder.getKey()).append('|')
                        .append(slotCountHolder.getValue().count).append('|')
                        .append(slotCountHolder.getValue().interceptedCount).append('\n');
            }

        } else {
            stringBuilder.append(point).append('|').append(monitorKey).append('|')
                    .append(conf.getPeriod()).append('|').append(timeFormatOfSecond).append('|')
                    .append(keySlot.getCountHolder(point).count.get()).append('|')
                    .append(keySlot.getCountHolder(point).interceptedCount.get()).append('\n');
        }
        return stringBuilder.toString();
    }

}
