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

package com.alibaba.nacos.plugin.control.impl;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosTpsControlManagerTest {
    
    @Test
    void testRegisterTpsPoint1() {
        
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        nacosTpsControlManager.registerTpsPoint("test");
        
        assertTrue(nacosTpsControlManager.getPoints().containsKey("test"));
    }
    
    @Test
    void testRegisterTpsPoint2() {
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        TpsControlRule tpsLimitRule = new TpsControlRule();
        nacosTpsControlManager.applyTpsRule("test", tpsLimitRule);
        nacosTpsControlManager.registerTpsPoint("test");
        
        assertTrue(nacosTpsControlManager.getPoints().containsKey("test"));
    }
    
    @Test
    void testApplyTpsRule1() {
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        TpsControlRule tpsLimitRule = new TpsControlRule();
        nacosTpsControlManager.applyTpsRule("test", tpsLimitRule);
        
        assertTrue(nacosTpsControlManager.getRules().containsKey("test"));
    }
    
    @Test
    void testApplyTpsRule2() {
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        nacosTpsControlManager.applyTpsRule("test", null);
        
        assertFalse(nacosTpsControlManager.getRules().containsKey("test"));
    }
    
    @Test
    void testCheck() {
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        nacosTpsControlManager.registerTpsPoint("test");
        final TpsControlRule tpsLimitRule = new TpsControlRule();
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsLimitRule.setPointRule(ruleDetail);
        tpsLimitRule.setPointName("test");
        nacosTpsControlManager.applyTpsRule("test", tpsLimitRule);
        
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName("test");
        tpsCheckRequest.setTimestamp(timeMillis);
        TpsCheckResponse check = nacosTpsControlManager.check(tpsCheckRequest);
        assertTrue(check.isSuccess());
    }
}
