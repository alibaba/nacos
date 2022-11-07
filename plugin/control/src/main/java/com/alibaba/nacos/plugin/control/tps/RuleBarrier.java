package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

import java.util.concurrent.TimeUnit;

public abstract class RuleBarrier {
    
    
    private TimeUnit period;
    
    private String barrierName;
    
    private String ruleName;
    
    private String pattern;
    
    private long maxCount;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    private String model;
    
    private int order;
    
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
    
    public String getBarrierName() {
        return barrierName;
    }
    
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
     * @param barrierCheckRequest
     * @return
     */
    abstract public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest);
    
    /**
     * rollback tps.
     *
     * @param barrierCheckRequest
     * @return
     */
    abstract public void rollbackTps(BarrierCheckRequest barrierCheckRequest);
    
    
    /**
     * @param ruleDetail
     */
    abstract public void applyRuleDetail(RuleDetail ruleDetail);
    
    
    /**
     * get metrics.
     *
     * @param timeStamp
     * @return
     */
    abstract public TpsMetrics getMetrics(long timeStamp);
    
    /**
     *
     */
    public void clearLimitRule() {
        this.maxCount = -1;
        this.monitorType = MonitorType.MONITOR.getType();
    }
}