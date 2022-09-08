package com.alibaba.nacos.plugin.control.tps.rule;

import java.util.concurrent.TimeUnit;

public class RuleDetail {
    
    private String pattern;
    
    long maxCount = -1;
    
    boolean printLog = false;
    
    TimeUnit period = TimeUnit.SECONDS;
    
    public static final String MODEL_FUZZY = "FUZZY";
    
    public static final String MODEL_PROTO = "PROTO";
    
    String model = MODEL_FUZZY;
    
    /**
     * monitor/intercept.
     */
    String monitorType = "";
    
    private int order;
    
    public RuleDetail() {
    
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public RuleDetail(long maxCount, TimeUnit period, String model, String monitorType) {
        this.maxCount = maxCount;
        this.period = period;
        this.model = model;
        this.monitorType = monitorType;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public boolean isPrintLog() {
        return printLog;
    }
    
    public void setPrintLog(boolean printLog) {
        this.printLog = printLog;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
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
