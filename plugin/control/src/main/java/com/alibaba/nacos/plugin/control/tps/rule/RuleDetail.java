package com.alibaba.nacos.plugin.control.tps.rule;

import java.util.concurrent.TimeUnit;

public class RuleDetail {
    
    String ruleName;
    
    long maxCount = -1;
    
    TimeUnit period = TimeUnit.SECONDS;
    
    /**
     * monitor/intercept.
     */
    String monitorType = "";
    
    public RuleDetail() {
    
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public TimeUnit getPeriod() {
        return period;
    }
    
    public void setPeriod(TimeUnit period) {
        this.period = period;
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
    
    @Override
    public String toString() {
        return "Rule{" + "maxTps=" + maxCount + ", monitorType='" + monitorType + '\'' + '}';
    }
}
