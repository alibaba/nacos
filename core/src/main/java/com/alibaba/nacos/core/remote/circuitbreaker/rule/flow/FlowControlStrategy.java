/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.circuitbreaker.*;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.fasterxml.jackson.core.type.TypeReference;
import io.jsonwebtoken.io.IOException;
import org.apache.commons.collections.MapUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author czf
 */
public class FlowControlStrategy extends CircuitBreakerStrategy {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final Map<String, FlowControlMonitor> pointToMonitorMap = new ConcurrentHashMap<>();

    @Override
    public String getRuleName() {
        return "flowControl";
    }

    @Override
    public void registerPoint(String pointName) {
        pointToMonitorMap.putIfAbsent(pointName, new FlowControlMonitor(pointName));
    }

    @Override
    public Map<String, CircuitBreakerMonitor> getPointToMonitorMap() {
        Map<String, CircuitBreakerMonitor> retMap = new HashMap<>();
        if (MapUtils.isNotEmpty(pointToMonitorMap)) {
            retMap = pointToMonitorMap.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return retMap;
    }

    // Should not use this method, should use applyStrategyWithLoad instead.
    @Override
    public boolean applyStrategy(String pointName, List<MonitorKey> monitorKeyList) {
        return true;
    }

    @Override
    public boolean applyStrategyWithLoad(String pointName, List<MonitorKey> monitorKeyList, long load) {
        if (pointToMonitorMap.containsKey(pointName)) {
            FlowControlMonitor pointMonitor = pointToMonitorMap.get(pointName);
            return pointMonitor.applyFlowControl(monitorKeyList, load);
        }
        return true;
    }

    @Override
    public void applyRule(String pointName, CircuitBreakerConfig config, Map<String, CircuitBreakerConfig> keyConfigMap) {
        FlowControlConfig castedPointConfig = (FlowControlConfig) config;

        if (pointToMonitorMap.containsKey(pointName)) {
            FlowControlMonitor pointMonitor = pointToMonitorMap.get(pointName);
            pointMonitor.applyRule(false, castedPointConfig, keyConfigMap);
        } else {
            FlowControlMonitor newMonitor = new FlowControlMonitor(pointName, castedPointConfig);
            newMonitor.applyRule(false, castedPointConfig, keyConfigMap);
        }
    }

    @Override
    public CircuitBreakerStrategy getStrategy() {
        return this;
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
    public CircuitBreakerConfig deserializePointConfig(String content) throws IOException {
        return StringUtils.isBlank(content) ? new FlowControlConfig()
                : JacksonUtils.toObj(content, FlowControlConfig.class);
    }

    @Override
    public Map<String, CircuitBreakerConfig> deserializeMonitorKeyConfig(String content) throws IOException {
        TypeReference<Map<String,FlowControlConfig>> typeRef
                = new TypeReference<Map<String, FlowControlConfig>>() {};
        Map<String,FlowControlConfig> configMap = StringUtils.isBlank(content) ? new HashMap<>()
                : JacksonUtils.toObj(content, typeRef);

        Map<String,CircuitBreakerConfig> retMap = new HashMap<>();
        if (MapUtils.isNotEmpty(configMap)) {
            retMap = configMap.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return retMap;
    }

    @Override
    public String reportMonitorPoint(CircuitBreaker.ReportTime reportTime, CircuitBreakerRecorder pointRecorder) {
        FlowControlRecorder tpsPoint = (FlowControlRecorder) pointRecorder;
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
        FlowControlConfig conf = (FlowControlConfig) tpsPoint.getConfig();
        FlowControlRecorder.LoadCountHolder holder = (FlowControlRecorder.LoadCountHolder) pointSlot.getCountHolder(point);
        stringBuilder.append("flow control|").append(point).append('|').append("point|").append(conf.getPeriod())
                .append('|').append(formatString).append('|')
                .append(holder.count.get()).append('|')
                .append(holder.load.get()).append('|')
                .append(holder.interceptedCount.get()).append('|')
                .append(holder.interceptedLoad.get()).append('\n');
        return stringBuilder.toString();
    }

    @Override
    public String reportMonitorKeys(CircuitBreaker.ReportTime reportTime, String monitorKey, CircuitBreakerRecorder pointRecorder) {
        long lastReportSecond = reportTime.lastReportSecond;
        long lastReportMinutes = reportTime.lastReportMinutes;
        long now = reportTime.now;
        FlowControlRecorder ipRecord = (FlowControlRecorder) pointRecorder;
        String point = pointRecorder.getPointName();

        StringBuilder stringBuilder = new StringBuilder();
        FlowControlConfig conf = (FlowControlConfig) ipRecord.getConfig();
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

        String timeFormatOfSecond = CircuitBreakerMonitor.getTimeFormatOfSecond(keySlot.time);
        reportTime.tempMinutes = keySlot.time;
        if (ipRecord.isProtoModel()) {
            Map<String, FlowControlRecorder.LoadCountHolder> keySlots = ((FlowControlRecorder.MultiKeyFlowControlSlot) keySlot).keySlots;
            for (Map.Entry<String, FlowControlRecorder.LoadCountHolder> slotCountHolder : keySlots.entrySet()) {
                stringBuilder.append("flow control|").append(point).append('|').append(monitorKey).append('|')
                        .append(conf.getPeriod()).append('|').append(timeFormatOfSecond).append('|')
                        .append(slotCountHolder.getKey()).append('|')
                        .append(slotCountHolder.getValue().count.get()).append('|')
                        .append(slotCountHolder.getValue().load.get()).append('|')
                        .append(slotCountHolder.getValue().interceptedCount.get()).append('|')
                        .append(slotCountHolder.getValue().interceptedLoad.get()).append('\n');
            }

        } else {
            FlowControlRecorder.LoadCountHolder holder = (FlowControlRecorder.LoadCountHolder) keySlot.getCountHolder(point);
            stringBuilder.append("flow control|").append(point).append('|').append(monitorKey).append('|')
                    .append(conf.getPeriod()).append('|').append(timeFormatOfSecond).append('|')
                    .append(holder.count.get()).append('|')
                    .append(holder.load).append('|')
                    .append(holder.interceptedCount.get()).append('|')
                    .append(holder.interceptedLoad).append('\n');
        }
        return stringBuilder.toString();
    }
}
