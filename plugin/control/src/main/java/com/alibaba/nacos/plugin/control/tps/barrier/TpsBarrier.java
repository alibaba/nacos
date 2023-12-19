/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.tps.barrier;

import com.alibaba.nacos.plugin.control.tps.barrier.creator.RuleBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.LocalSimpleCountBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.concurrent.TimeUnit;

/**
 * tps barrier for tps point.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class TpsBarrier {
    
    protected RuleBarrierCreator ruleBarrierCreator;
    
    protected String pointName;
    
    protected RuleBarrier pointBarrier;
    
    public TpsBarrier(String pointName) {
        this.pointName = pointName;
        this.ruleBarrierCreator = new LocalSimpleCountBarrierCreator();
        this.pointBarrier = ruleBarrierCreator.createRuleBarrier(pointName, pointName, TimeUnit.SECONDS);
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
