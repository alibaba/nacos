package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;

public class NacosRuleBarrier extends RuleBarrier {
    
    @Override
    public String getBarrierName() {
        return null;
    }
    
    @Override
    public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
        return null;
    }
    
    @Override
    public void rollbackTps(BarrierCheckRequest barrierCheckRequest) {
    
    }
    
    @Override
    public void applyRuleDetail(RuleDetail ruleDetail) {
    
    }
    
    @Override
    public TpsMetrics getMetrics(long timeStamp) {
        return null;
    }
}
