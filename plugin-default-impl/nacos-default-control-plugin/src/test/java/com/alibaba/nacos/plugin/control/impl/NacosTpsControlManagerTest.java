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
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.barrier.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        RuleDetail ruleDetail = new RuleDetail();
        tpsLimitRule.setPointRule(ruleDetail);
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
    
    @Test
    void testRegisterTpsPointWithExistingRule() {
        NacosTpsControlManager nacosTpsControlManager = createManagerWithoutReporter();
        TpsControlRule tpsLimitRule = createRule("test");
        nacosTpsControlManager.applyTpsRule("test", tpsLimitRule);
        
        nacosTpsControlManager.registerTpsPoint("test");
        
        assertTrue(nacosTpsControlManager.getPoints().containsKey("test"));
        assertEquals(tpsLimitRule, nacosTpsControlManager.getRules().get("test"));
    }
    
    @Test
    void testCheckSkipWhenPointMissing() {
        NacosTpsControlManager nacosTpsControlManager = createManagerWithoutReporter();
        
        TpsCheckResponse response = nacosTpsControlManager.check(createRequest("missing"));
        
        assertTrue(response.isSuccess());
        assertEquals(TpsResultCode.CHECK_SKIP, response.getCode());
        assertEquals("skip", response.getMessage());
    }
    
    @Test
    void testCheckSkipWhenBarrierThrows() {
        NacosTpsControlManager nacosTpsControlManager = createManagerWithoutReporter();
        nacosTpsControlManager.getPoints().put("test", new ThrowingTpsBarrier("test"));
        
        TpsCheckResponse response = nacosTpsControlManager.check(createRequest("test"));
        
        assertTrue(response.isSuccess());
        assertEquals(TpsResultCode.CHECK_SKIP, response.getCode());
        assertEquals("skip", response.getMessage());
    }
    
    @Test
    void testMetricsReporterReportsAndSkipsDuplicateSecond() {
        NacosTpsControlManager nacosTpsControlManager = createManagerWithoutReporter();
        long timestamp = TimeUnit.SECONDS.toMillis(12345);
        TpsMetrics metrics = new TpsMetrics("test", "point", timestamp, TimeUnit.SECONDS);
        metrics.setCounter(new TpsMetrics.Counter(3, 1));
        nacosTpsControlManager.getPoints().put("test", new MetricsTpsBarrier("test", metrics));
        NacosTpsControlManager.TpsMetricsReporter reporter =
            nacosTpsControlManager.new TpsMetricsReporter();
        
        reporter.run();
        reporter.run();
        
        assertEquals(timestamp, reporter.lastReportSecond);
    }
    
    @Test
    void testMetricsReporterCatchesThrowable() {
        NacosTpsControlManager nacosTpsControlManager = createManagerWithoutReporter();
        nacosTpsControlManager.getPoints().put("test", new ThrowingMetricsTpsBarrier("test"));
        NacosTpsControlManager.TpsMetricsReporter reporter =
            nacosTpsControlManager.new TpsMetricsReporter();
        
        reporter.run();
        
        assertEquals(0L, reporter.lastReportSecond);
    }
    
    private NacosTpsControlManager createManagerWithoutReporter() {
        NacosTpsControlManager nacosTpsControlManager = new NacosTpsControlManager();
        nacosTpsControlManager.executorService.shutdownNow();
        return nacosTpsControlManager;
    }
    
    private TpsCheckRequest createRequest(String pointName) {
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setTimestamp(System.currentTimeMillis());
        return tpsCheckRequest;
    }
    
    private TpsControlRule createRule(String pointName) {
        TpsControlRule tpsLimitRule = new TpsControlRule();
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsLimitRule.setPointRule(ruleDetail);
        tpsLimitRule.setPointName(pointName);
        return tpsLimitRule;
    }
    
    private static class ThrowingTpsBarrier extends TpsBarrier {
        
        ThrowingTpsBarrier(String pointName) {
            super(pointName);
        }
        
        @Override
        public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
            throw new IllegalStateException("mock error");
        }
        
        @Override
        public void applyRule(TpsControlRule newControlRule) {
        }
    }
    
    private static class MetricsTpsBarrier extends TpsBarrier {
        
        MetricsTpsBarrier(String pointName, TpsMetrics metrics) {
            super(pointName);
            pointBarrier = new FixedRuleBarrier(metrics);
        }
        
        @Override
        public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
            return new TpsCheckResponse(true, TpsResultCode.PASS_BY_POINT, "success");
        }
        
        @Override
        public void applyRule(TpsControlRule newControlRule) {
        }
    }
    
    private static class ThrowingMetricsTpsBarrier extends TpsBarrier {
        
        ThrowingMetricsTpsBarrier(String pointName) {
            super(pointName);
            pointBarrier = new ThrowingMetricsRuleBarrier();
        }
        
        @Override
        public TpsCheckResponse applyTps(TpsCheckRequest tpsCheckRequest) {
            return new TpsCheckResponse(true, TpsResultCode.PASS_BY_POINT, "success");
        }
        
        @Override
        public void applyRule(TpsControlRule newControlRule) {
        }
    }
    
    private static class FixedRuleBarrier extends RuleBarrier {
        
        private final TpsMetrics metrics;
        
        FixedRuleBarrier(TpsMetrics metrics) {
            this.metrics = metrics;
        }
        
        @Override
        public String getBarrierName() {
            return "fixed";
        }
        
        @Override
        public TpsCheckResponse applyTps(BarrierCheckRequest barrierCheckRequest) {
            return new TpsCheckResponse(true, TpsResultCode.PASS_BY_POINT, "success");
        }
        
        @Override
        public void applyRuleDetail(RuleDetail ruleDetail) {
        }
        
        @Override
        public TpsMetrics getMetrics(long timeStamp) {
            return metrics;
        }
    }
    
    private static class ThrowingMetricsRuleBarrier extends FixedRuleBarrier {
        
        ThrowingMetricsRuleBarrier() {
            super(null);
        }
        
        @Override
        public TpsMetrics getMetrics(long timeStamp) {
            throw new IllegalStateException("mock error");
        }
    }
}
