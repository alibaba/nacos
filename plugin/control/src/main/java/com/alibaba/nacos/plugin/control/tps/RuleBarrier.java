package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

import java.util.concurrent.TimeUnit;

public abstract class RuleBarrier {
    
    private TimeUnit period;
    
    private String pointName;
    
    private String ruleName;
    
    private String pattern;
    
    private long maxCount;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    private String model;
    
    private int order;
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    public void setPeriod(TimeUnit period) {
        this.period = period;
    }
    
    public abstract String getBarrierName();
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
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
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public boolean isProtoModel() {
        return RuleModel.PROTO.name().equalsIgnoreCase(this.model);
    }
    
    public String getLimitMsg() {
        return String.format("[Name:%s,Pattern:%s,Period:%s,MaxCount:%s]", ruleName, pattern, period, maxCount);
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