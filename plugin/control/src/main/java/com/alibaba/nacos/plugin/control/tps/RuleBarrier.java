package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

import java.util.concurrent.TimeUnit;

public abstract class RuleBarrier {
    
    
    private TimeUnit period;
    
    private String name;
    
    private String pattern;
    
    private long maxCount;
    
    /**
     * monitor/intercept.
     */
    private String monitorType = MonitorType.MONITOR.type;
    
    private String model;
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    public void setPeriod(TimeUnit period) {
        this.period = period;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
    
    public String getLimitMsg() {
        return String.format("[Name:%s,Pattern:%s,Period:%s,MaxCount:%s]", name, pattern, period, maxCount);
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest
     * @return
     */
    abstract public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest);
    
    /**
     * rollback tps.
     *
     * @param tpsCheckRequest
     * @return
     */
    abstract public void rollbackTps(TpsCheckRequest tpsCheckRequest);
    
    
    /**
     * @param ruleDetail
     */
    abstract public void applyRuleDetail(RuleDetail ruleDetail);
    
    /**
     *
     */
    abstract public void clearLimitRule();
}