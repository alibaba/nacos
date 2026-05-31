/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.TpsBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultTpsControlManagerTest {
    
    @Test
    void testRegisterApplyCheckAndName() {
        TestDefaultTpsControlManager manager = new TestDefaultTpsControlManager();
        TpsControlRule rule = createRule();
        manager.applyTpsRule("point", rule);
        
        manager.registerTpsPoint("point");
        manager.registerTpsPoint("point");
        
        assertEquals(1, manager.getPoints().size());
        assertSame(rule, manager.getRules().get("point"));
        assertSame(rule, manager.createdBarrier.appliedRule);
        assertEquals("noLimit", manager.getName());
        assertEquals(TpsResultCode.CHECK_SKIP, manager.check(new TpsCheckRequest()).getCode());
        
        manager.applyTpsRule("point", null);
        assertFalse(manager.getRules().containsKey("point"));
        assertEquals(2, manager.createdBarrier.applyCount);
        
        TpsControlRule emptyRule = new TpsControlRule();
        manager.applyTpsRule("point", emptyRule);
        assertFalse(manager.getRules().containsKey("point"));
        assertEquals(3, manager.createdBarrier.applyCount);
    }
    
    private TpsControlRule createRule() {
        TpsControlRule rule = new TpsControlRule();
        rule.setPointName("point");
        RuleDetail detail = new RuleDetail();
        detail.setMaxCount(1L);
        rule.setPointRule(detail);
        return rule;
    }
    
    private static class TestDefaultTpsControlManager extends DefaultTpsControlManager {
        
        private TestTpsBarrier createdBarrier;
        
        @Override
        protected TpsBarrierCreator buildTpsBarrierCreator() {
            return new TpsBarrierCreator() {
                
                @Override
                public String getName() {
                    return "test";
                }
                
                @Override
                public TpsBarrier createTpsBarrier(String pointName) {
                    createdBarrier = new TestTpsBarrier(pointName);
                    return createdBarrier;
                }
            };
        }
    }
    
    private static class TestTpsBarrier extends TpsBarrier {
        
        private TpsControlRule appliedRule;
        
        private int applyCount;
        
        TestTpsBarrier(String pointName) {
            super(pointName);
        }
        
        @Override
        public com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse applyTps(
            TpsCheckRequest tpsCheckRequest) {
            return null;
        }
        
        @Override
        public void applyRule(TpsControlRule newControlRule) {
            applyCount++;
            appliedRule = newControlRule;
        }
    }
}
