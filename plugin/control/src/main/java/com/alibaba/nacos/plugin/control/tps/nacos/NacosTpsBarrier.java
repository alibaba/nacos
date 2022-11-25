package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

/**
 * tps barrier for tps point.
 */
public class NacosTpsBarrier extends TpsBarrier {
    
    public NacosTpsBarrier(String pointName) {
        super(pointName);
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
        
        BarrierCheckRequest pointCheckRequest = new BarrierCheckRequest();
        pointCheckRequest.setCount(tpsCheckRequest.getCount());
        pointCheckRequest.setPointName(super.getPointName());
        pointCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp());
        return super.getPointBarrier().applyTps(pointCheckRequest);
    }
    
    /**
     * apply rule.
     *
     * @param newControlRule newControlRule.
     */
    public synchronized void applyRule(TpsControlRule newControlRule) {
        Loggers.CONTROL.info("Apply tps control rule start,pointName=[{}]  ", this.getPointName());
        
        //1.reset all monitor point for null.
        if (newControlRule == null || newControlRule.getPointRule() == null) {
            Loggers.CONTROL.info("Clear all tps control rule ,pointName=[{}]  ", this.getPointName());
            super.getPointBarrier().clearLimitRule();
            return;
        }
        
        //2.check point rule.
        RuleDetail newPointRule = newControlRule.getPointRule();
        
        Loggers.CONTROL.info("Update  point  control rule ,pointName=[{}],original maxTps={}, new maxTps={}"
                        + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                this.pointBarrier.getMaxCount(), newPointRule.getMaxCount(), this.pointBarrier.getMonitorType(),
                newPointRule.getMonitorType());
        this.pointBarrier.applyRuleDetail(newPointRule);
        
        Loggers.CONTROL.info("Apply tps control rule end,pointName=[{}]  ", this.getPointName());
        
    }
}
