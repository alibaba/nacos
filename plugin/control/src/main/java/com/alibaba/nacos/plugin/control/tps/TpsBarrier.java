package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * tps barrier for tps point.
 */
public abstract class TpsBarrier {
    
    protected String pointName;
    
    protected RuleBarrier pointBarrier;
    
    protected List<RuleBarrier> patternBarriers = new ArrayList<>();
    
    public TpsBarrier(String pointName) {
        this.pointName = pointName;
        pointBarrier = ruleBarrierCreator
                .createRuleBarrier(pointName, pointName, "", TimeUnit.SECONDS, RuleModel.FUZZY.name());
    }
    
    static protected RuleBarrierCreator ruleBarrierCreator;
    
    static {
        String tpsBarrierCreator = ControlConfigs.getInstance().getTpsBarrierCreator();
        Collection<RuleBarrierCreator> loadedCreators = NacosServiceLoader.load(RuleBarrierCreator.class);
        for (RuleBarrierCreator barrierCreator : loadedCreators) {
            if (tpsBarrierCreator.equalsIgnoreCase(barrierCreator.name())) {
                Loggers.CONTROL.info("Found tps rule creator of name : {}", tpsBarrierCreator);
                ruleBarrierCreator = barrierCreator;
                break;
            }
        }
        if (ruleBarrierCreator == null) {
            Loggers.CONTROL.warn("Fail to found tps rule creator of name : {},use  default local simple creator",
                    tpsBarrierCreator);
            ruleBarrierCreator = LocalSimpleCountBarrierCreator.getInstance();
        }
        
    }
    
    /**
     * apply tps.
     *
     * @param tpsCheckRequest tpsCheckRequest.
     * @return check current tps is allowed.
     */
    public abstract TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest);
    
    public RuleBarrier getPointBarrier() {
        return pointBarrier;
    }
    
    public List<RuleBarrier> getPatternBarriers() {
        return patternBarriers;
    }
    
    public String getPointName() {
        return pointName;
    }
    
    /**
     * apply rule.
     *
     * @param newControlRule newControlRule.
     */
    public abstract void applyRule(TpsControlRule newControlRule);
}
