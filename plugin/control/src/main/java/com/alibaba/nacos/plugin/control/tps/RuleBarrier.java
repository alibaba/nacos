package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;

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
    
    public boolean isProtoModel() {
        return RuleModel.PROTO.name().equalsIgnoreCase(this.model);
    }
    
    public String getLimitMsg() {
        return String.format("[Name:%s,Pattern:%s,Period:%s,MaxCount:%s]", name, pattern, period, maxCount);
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
     *
     */
    public void clearLimitRule() {
        this.maxCount = -1;
        this.monitorType = MonitorType.MONITOR.getType();
    }
}