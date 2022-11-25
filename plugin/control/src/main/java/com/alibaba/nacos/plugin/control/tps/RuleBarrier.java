package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

import java.util.concurrent.TimeUnit;

public abstract class RuleBarrier {
    
    private TimeUnit period;
    
    private String pointName;
    
    private long maxCount;
    
    private String ruleName;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
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
        return MonitorType.MONITOR.type.equalsIgnoreCase(this.monitorType);
    }
    
    public String getLimitMsg() {
        return String.format("[Period:%s,MaxCount:%s]", period, maxCount);
    }
    
    /**
     * apply tps.
     *
     * @param barrierCheckRequest barrierCheckRequest.
     * @return
     */
    public abstract TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest);
    
    /**
     * rollback tps.
     *
     * @param barrierCheckRequest barrierCheckRequest.
     * @return
     */
    public abstract void rollbackTps(BarrierCheckRequest barrierCheckRequest);
    
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