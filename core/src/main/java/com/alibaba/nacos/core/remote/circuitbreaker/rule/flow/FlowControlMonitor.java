package com.alibaba.nacos.core.remote.circuitbreaker.rule.flow;

import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerConfig;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerMonitor;
import com.alibaba.nacos.core.remote.circuitbreaker.CircuitBreakerRecorder;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKeyMatcher;
import com.alibaba.nacos.core.remote.control.MonitorType;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.collections.MapUtils;

import java.util.*;
import java.util.function.LongBinaryOperator;
import java.util.stream.Collectors;

public class FlowControlMonitor extends CircuitBreakerMonitor {

    public static final int DEFAULT_RECORD_SIZE = 10;

    private FlowControlRecorder flowRecorder;

    public Map<String, FlowControlRecorder> monitorKeysRecorder = new HashMap<>();

    public Map<String, FlowControlConfig> monitorKeysConfig = new HashMap<>();

    public FlowControlMonitor(String pointName) {
        this(pointName, new FlowControlConfig());
    }

    public FlowControlMonitor(String pointName, FlowControlConfig config) {
        // trim to second,uniform all tps control.
        this.startTime = getTrimMillsOfSecond(System.currentTimeMillis());
        this.pointName = pointName;
        this.flowRecorder = new FlowControlRecorder(pointName, startTime, DEFAULT_RECORD_SIZE, config);
    }

    @Override
    public void createAndPutNewRecorder(String key, CircuitBreakerConfig config) {
        this.monitorKeysRecorder.put(key, new FlowControlRecorder(pointName, startTime, DEFAULT_RECORD_SIZE, (FlowControlConfig) config));
    }

    @Override
    public Map<String, CircuitBreakerRecorder> getMonitorKeyRecorders() {
        return this.monitorKeysRecorder.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void stopAllMonitorClient() {
        monitorKeysRecorder.clear();
        monitorKeysConfig.clear();
    }

    @Override
    public void clearAllTpsConfigs() {
        // disable current config for current point
        flowRecorder.getConfig().setIsActive(false);
        monitorKeysConfig.clear();
        monitorKeysRecorder.clear();
    }

    private boolean isInterceptMode(String monitorType) {
        return MonitorType.INTERCEPT.getType().equals(monitorType);
    }
    /**
     * increase tps.
     *
     * @param monitorKeys monitorKeys.
     * @return check current tps is allowed.
     */
    public boolean applyFlowControl(List<MonitorKey> monitorKeys, long load) {

        long now = System.currentTimeMillis();
        CircuitBreakerRecorder.Slot currentSlot = flowRecorder.createSlotIfAbsent(now);
        FlowControlRecorder.LoadCountHolder pointCountHolder = (FlowControlRecorder.LoadCountHolder) currentSlot.getCountHolder(pointName);

        // Find connectionId for the current monitorKeys
        String connectionId = getConnectionId(monitorKeys);
        LongBinaryOperator sumOperator = Long::sum;

        //1.check monitor keys.
        List<FlowControlRecorder.LoadCountHolder> passedSlots = new ArrayList<>();
        for (MonitorKey monitorKey : monitorKeys) {
            for (Map.Entry<String, FlowControlRecorder> entry : monitorKeysRecorder.entrySet()) {

                // ConnectionIdMonitorKey should not be included
                if (!CONNECTION_ID.equals(monitorKey.getType())
                        && MonitorKeyMatcher.matchWithType(entry.getKey(), monitorKey.build())) {
                    FlowControlRecorder recorderKey = entry.getValue();
                    CircuitBreakerRecorder.Slot currentKeySlot = recorderKey.createSlotIfAbsent(now);

                    // get max count status from config instead of directly from the TpsRecorder
                    FlowControlConfig config = (FlowControlConfig) recorderKey.getConfig();
                    long maxLoad = config.getMaxLoad();
                    FlowControlRecorder.LoadCountHolder countHolder = (FlowControlRecorder.LoadCountHolder)
                            currentKeySlot.getCountHolder(pointName);
                    boolean overLimit = maxLoad >= 0 && countHolder.load.longValue() + load >= maxLoad;
                    if (overLimit) {
                        Loggers.TPS_CONTROL_DETAIL
                                .info("[{}]flow control over limit ,pointName=[{}],barrier=[{}]，monitorModel={},maxTps={}",
                                        connectionId, this.getPointName(), entry.getKey(),
                                        config.getMonitorType(), maxLoad + "/" + config.getPeriod());

                        // add intercepted load and count.
                        if (isInterceptMode(config.getMonitorType())) {
                            FlowControlRecorder.LoadCountHolder currentHolder = (FlowControlRecorder.LoadCountHolder)
                                    currentKeySlot.getCountHolder(pointName);
                            currentHolder.interceptedCount.incrementAndGet();
                            currentHolder.interceptedLoad.accumulateAndGet(load, sumOperator);
                            return false;
                        }
                    } else {
                        passedSlots.add(countHolder);
                    }
                }
            }
        }

        //2.check total tps.
        FlowControlConfig conf = (FlowControlConfig) flowRecorder.getConfig();
        long maxWorkLoad = conf.getMaxLoad();
        boolean overLimit = maxWorkLoad >= 0 && pointCountHolder.load.longValue() + load >= maxWorkLoad;
        if (overLimit) {
            Loggers.TPS_CONTROL_DETAIL
                    .info("[{}]Tps over limit ,pointName=[{}],barrier=[{}]，monitorType={}", connectionId,
                            this.getPointName(), "pointRule", flowRecorder.getConfig().getMonitorType());
            if (isInterceptMode(flowRecorder.getConfig().getMonitorType())) {
                currentSlot.getCountHolder(pointName).interceptedCount.incrementAndGet();
                ((FlowControlRecorder.LoadCountHolder) currentSlot.getCountHolder(pointName))
                        .interceptedLoad.accumulateAndGet(load, sumOperator);
                return false;
            }
        }

        currentSlot.getCountHolder(pointName).count.incrementAndGet();
        for (FlowControlRecorder.LoadCountHolder passedSlot : passedSlots) {
            passedSlot.count.incrementAndGet();
            passedSlot.load.accumulateAndGet(load, sumOperator);
        }
        //3.check pass.
        return true;
    }

    public FlowControlRecorder getTpsRecorder() {
        return flowRecorder;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    private String getConnectionId(List<MonitorKey> monitorKeys) {
        for (MonitorKey monitorKey : monitorKeys) {
            if (CONNECTION_ID.equals(monitorKey.getType())) {
                return monitorKey.getKey();
            }
        }
        return "";
    }

    @Override
    public CircuitBreakerRecorder getPointRecorder() {
        return flowRecorder;
    }

    @Override
    public Map<String, CircuitBreakerRecorder> getMonitorKeysRecorder() {
        if (MapUtils.isNotEmpty(monitorKeysRecorder)) {
            return monitorKeysRecorder.entrySet()
                    .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return new HashMap<>();
    }

    @Override
    public CircuitBreakerRecorder getCurrentRecorder() {
        return this.flowRecorder;
    }
}
