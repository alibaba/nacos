/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * tps barrier for tps point.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class TpsBarrier {
    
    protected String pointName;
    
    protected RuleBarrier pointBarrier;
    
    public TpsBarrier(String pointName) {
        this.pointName = pointName;
        pointBarrier = ruleBarrierCreator.createRuleBarrier(pointName, pointName, TimeUnit.SECONDS);
    }
    
    protected static RuleBarrierCreator ruleBarrierCreator;
    
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
    
    public static void setRuleBarrierCreator(RuleBarrierCreator ruleBarrierCreatorInstance) {
        ruleBarrierCreator = ruleBarrierCreatorInstance;
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
