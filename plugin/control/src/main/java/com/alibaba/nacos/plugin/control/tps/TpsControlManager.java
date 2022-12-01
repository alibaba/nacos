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
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParserProxy;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleStorageProxy;
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
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     */
    public abstract void registerTpsPoint(String pointName);
    
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
            TpsControlRule tpsLimitRule = RuleParserProxy.getInstance().parseTpsRule(localRuleContent);
            this.applyTpsRule(pointName, tpsLimitRule);
        } else {
            Loggers.CONTROL.info("No tps control rule of {} found  ", pointName, localRuleContent);
        }
    }
    
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
