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

package com.alibaba.nacos.plugin.control.tps.barrier;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.LocalSimpleCountBarrierCreator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCountRuleBarrierTest {
    
    @Test
    void testApplyTpsMonitorAndInterceptBranches() {
        TestSimpleCountRuleBarrier barrier =
            new TestSimpleCountRuleBarrier("point", "rule", TimeUnit.SECONDS);
        BarrierCheckRequest request = new BarrierCheckRequest();
        request.setTimestamp(1000L);
        request.setCount(2L);
        
        TpsCheckResponse monitorResponse = barrier.applyTps(request);
        assertTrue(monitorResponse.isSuccess());
        assertEquals(2L, barrier.counter.total);
        
        barrier.setMonitorType(MonitorType.INTERCEPT.getType());
        barrier.setMaxCount(4L);
        assertTrue(barrier.applyTps(request).isSuccess());
        TpsCheckResponse denied = barrier.applyTps(request);
        assertFalse(denied.isSuccess());
        assertTrue(denied.getMessage().contains("tps over limit"));
    }
    
    @Test
    void testMetricsTrimAndRuleDetailBranches() {
        TestSimpleCountRuleBarrier barrier =
            new TestSimpleCountRuleBarrier("point", "rule", TimeUnit.SECONDS);
        assertEquals(1000L, barrier.trimTimeStamp(1234L));
        barrier.setPeriod(TimeUnit.MINUTES);
        assertEquals(60000L, barrier.trimTimeStamp(61000L));
        barrier.setPeriod(TimeUnit.HOURS);
        assertEquals(3600000L, barrier.trimTimeStamp(3601000L));
        barrier.setPeriod(TimeUnit.DAYS);
        assertEquals(1000L, barrier.trimTimeStamp(1234L));
        
        assertNull(barrier.getMetrics(1234L));
        barrier.counter.total = 7L;
        TpsMetrics metrics = barrier.getMetrics(1234L);
        assertNotNull(metrics);
        assertEquals(7L, metrics.getCounter().getPassCount());
        
        RuleDetail samePeriod = new RuleDetail();
        samePeriod.setMaxCount(10L);
        samePeriod.setMonitorType(MonitorType.INTERCEPT.getType());
        samePeriod.setPeriod(TimeUnit.DAYS);
        barrier.applyRuleDetail(samePeriod);
        assertEquals(TimeUnit.DAYS, barrier.getPeriod());
        assertEquals(10L, barrier.getMaxCount());
        
        RuleDetail newPeriod = new RuleDetail();
        newPeriod.setRuleName("newRule");
        newPeriod.setPeriod(TimeUnit.MINUTES);
        newPeriod.setMaxCount(5L);
        newPeriod.setMonitorType(MonitorType.MONITOR.getType());
        barrier.applyRuleDetail(newPeriod);
        assertEquals(TimeUnit.MINUTES, barrier.getPeriod());
        assertEquals("newRule", barrier.counter.getName());
    }
    
    @Test
    void testRuleBarrierAccessorsAndClear() {
        TestSimpleCountRuleBarrier barrier =
            new TestSimpleCountRuleBarrier("point", "rule", TimeUnit.SECONDS);
        barrier.setMaxCount(9L);
        barrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        assertEquals("test", barrier.getBarrierName());
        assertEquals("rule", barrier.getRuleName());
        assertEquals("point", barrier.getPointName());
        assertFalse(barrier.isMonitorType());
        assertTrue(barrier.getLimitMsg().contains("\"limitCount\":\"9\""));
        
        barrier.clearLimitRule();
        assertEquals(-1L, barrier.getMaxCount());
        assertTrue(barrier.isMonitorType());
    }
    
    @Test
    void testLocalSimpleCountBarrierCreator() {
        LocalSimpleCountBarrierCreator creator = LocalSimpleCountBarrierCreator.getInstance();
        
        RuleBarrier barrier = creator.createRuleBarrier("point", "rule", TimeUnit.SECONDS);
        
        assertEquals("localsimplecountor", creator.name());
        assertEquals("localsimplecount", barrier.getBarrierName());
    }
    
    private static class TestSimpleCountRuleBarrier extends SimpleCountRuleBarrier {
        
        private TestRateCounter counter;
        
        TestSimpleCountRuleBarrier(String pointName, String ruleName, TimeUnit period) {
            super(pointName, ruleName, period);
        }
        
        @Override
        public RateCounter createSimpleCounter(String name, TimeUnit period) {
            counter = new TestRateCounter(name, period);
            return counter;
        }
        
        @Override
        public String getBarrierName() {
            return "test";
        }
    }
    
    private static class TestRateCounter extends RateCounter {
        
        private long total;
        
        TestRateCounter(String name, TimeUnit period) {
            super(name, period);
        }
        
        @Override
        public long add(long timestamp, long count) {
            total += count;
            return total;
        }
        
        @Override
        public boolean tryAdd(long timestamp, long countDelta, long upperLimit) {
            if (total + countDelta > upperLimit) {
                return false;
            }
            total += countDelta;
            return true;
        }
        
        @Override
        public long getCount(long timestamp) {
            return total;
        }
    }
}
