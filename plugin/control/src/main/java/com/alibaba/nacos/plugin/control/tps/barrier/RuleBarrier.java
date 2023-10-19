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

package com.alibaba.nacos.plugin.control.tps.barrier;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * rule barrier.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RuleBarrier {
    
    private TimeUnit period;
    
    private String pointName;
    
    private long maxCount;
    
    private String ruleName;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.getType();
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    public void setPeriod(TimeUnit period) {
        this.period = period;
    }
    
    /**
     * get barrier name.
     *
     * @return
     */
    public abstract String getBarrierName();
    
    public long getMaxCount() {
        return maxCount;
    }
    
    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }
    
    public String getMonitorType() {
        return monitorType;
    }
    
    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }
    
    public boolean isMonitorType() {
        return MonitorType.MONITOR.getType().equalsIgnoreCase(this.monitorType);
    }
    
    public String getLimitMsg() {
        Map<String, String> limitMsg = new HashMap<>(3);
        limitMsg.put("deniedType", "point");
        limitMsg.put("period", period.toString());
        limitMsg.put("limitCount", String.valueOf(maxCount));
        return JacksonUtils.toJson(limitMsg);
    }
    
    /**
     * apply tps.
     *
     * @param barrierCheckRequest barrierCheckRequest.
     * @return
     */
    public abstract TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest);
    
    /**
     * apply rule detail.
     *
     * @param ruleDetail ruleDetail.
     */
    public abstract void applyRuleDetail(RuleDetail ruleDetail);
    
    /**
     * get metrics.
     *
     * @param timeStamp timeStamp.
     * @return
     */
    public abstract TpsMetrics getMetrics(long timeStamp);
    
    /**
     * clear limit rule.
     */
    public void clearLimitRule() {
        this.maxCount = -1;
        this.monitorType = MonitorType.MONITOR.getType();
    }
}