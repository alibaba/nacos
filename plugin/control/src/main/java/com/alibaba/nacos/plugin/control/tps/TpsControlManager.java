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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.rule.parser.NacosTpsControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.parser.TpsControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.DefaultNacosTpsBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.TpsBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.Map;

/**
 * abstract tps control manager.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class TpsControlManager {
    
    private final TpsControlRuleParser tpsControlRuleParser;
    
    protected final TpsBarrierCreator tpsBarrierCreator;
    
    protected TpsControlManager() {
        this.tpsControlRuleParser = buildTpsControlRuleParser();
        this.tpsBarrierCreator = buildTpsBarrierCreator();
    }
    
    public TpsControlRuleParser getTpsControlRuleParser() {
        return tpsControlRuleParser;
    }
    
    protected TpsControlRuleParser buildTpsControlRuleParser() {
        return new NacosTpsControlRuleParser();
    }
    
    /**
     * Build tps barrier creator to creator tps barrier for each point.
     *
     * @return TpsBarrierCreator implementation for current plugin
     */
    protected TpsBarrierCreator buildTpsBarrierCreator() {
        return new DefaultNacosTpsBarrierCreator();
    }
    
    protected void initTpsRule(String pointName) {
        RuleStorageProxy ruleStorageProxy = RuleStorageProxy.getInstance();
        
        String localRuleContent = ruleStorageProxy.getLocalDiskStorage().getTpsRule(pointName);
        if (StringUtils.isNotBlank(localRuleContent)) {
            Loggers.CONTROL.info("Found local disk tps control rule of {},content ={}", pointName, localRuleContent);
        } else if (ruleStorageProxy.getExternalStorage() != null
                && ruleStorageProxy.getExternalStorage().getTpsRule(pointName) != null) {
            localRuleContent = ruleStorageProxy.getExternalStorage().getTpsRule(pointName);
            if (StringUtils.isNotBlank(localRuleContent)) {
                Loggers.CONTROL.info("Found external  tps control rule of {},content ={}", pointName, localRuleContent);
            }
        }
        
        if (StringUtils.isNotBlank(localRuleContent)) {
            TpsControlRule tpsLimitRule = tpsControlRuleParser.parseRule(localRuleContent);
            this.applyTpsRule(pointName, tpsLimitRule);
        } else {
            Loggers.CONTROL.info("No tps control rule of {} found,content ={}  ", pointName, localRuleContent);
        }
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     */
    public abstract void registerTpsPoint(String pointName);
    
    /**
     * get points.
     *
     * @return
     */
    public abstract Map<String, TpsBarrier> getPoints();
    
    /**
     * get rules.
     *
     * @return
     */
    public abstract Map<String, TpsControlRule> getRules();
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     * @param rule      rule.
     */
    public abstract void applyTpsRule(String pointName, TpsControlRule rule);
    
    /**
     * check tps result.
     *
     * @param tpsRequest TpsRequest.
     * @return check current tps is allowed.
     */
    public abstract TpsCheckResponse check(TpsCheckRequest tpsRequest);
    
    /**
     * get control manager name.
     *
     * @return
     */
    public abstract String getName();
}
