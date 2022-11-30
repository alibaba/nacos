package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.plugin.control.event.mse.TpsRequestDeniedEvent;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DeniedEventTest {
    
    
    @Test
    public void testTpsPointDenied() throws InterruptedException {
        String testTpsBarrier = "test_barrier" + System.currentTimeMillis();
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<TpsRequestDeniedEvent> subscriber = new Subscriber<TpsRequestDeniedEvent>() {
            @Override
            public void onEvent(TpsRequestDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                if (event.getTpsCheckRequest().getPointName().equalsIgnoreCase(testTpsBarrier)) {
                    Assert.assertFalse(event.isMonitorModel());
                    deniedEventCount.incrementAndGet();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TpsRequestDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 5tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 5) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse.getCode());
            }
        }
        
        Thread.sleep(500L);
        //check event
        Assert.assertEquals(5, deniedEventCount.get());
        
    }
    
    @Test
    public void testTpsPointMonitorOverLimit() throws InterruptedException {
        String testTpsBarrier = "test_barrier" + System.currentTimeMillis();
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<TpsRequestDeniedEvent> subscriber = new Subscriber<TpsRequestDeniedEvent>() {
            @Override
            public void onEvent(TpsRequestDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                if (testTpsBarrier.equalsIgnoreCase(event.getTpsCheckRequest().getPointName())) {
                    Assert.assertTrue(event.isMonitorModel());
                    deniedEventCount.incrementAndGet();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TpsRequestDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 5tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 5) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponse.getCode());
            }
        }
        
        Thread.sleep(500L);
        //check event
        Assert.assertEquals(5, deniedEventCount.get());
        
    }
    
    @Test
    public void testTpsPatternOverLimit() throws InterruptedException {
        String testTpsBarrier = "test_barrier" + System.currentTimeMillis();
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<TpsRequestDeniedEvent> subscriber = new Subscriber<TpsRequestDeniedEvent>() {
            @Override
            public void onEvent(TpsRequestDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                if (testTpsBarrier.equalsIgnoreCase(event.getTpsCheckRequest().getPointName())) {
                    System.out.println("isMonitorModel:" + event.isMonitorModel());
                    Assert.assertFalse(event.isMonitorModel());
                    deniedEventCount.incrementAndGet();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TpsRequestDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 5tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(50);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        MseRuleDetail clientIpDetail = new MseRuleDetail();
        clientIpDetail.setMaxCount(5);
        clientIpDetail.setPattern("clientIp:*");
        clientIpDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        clientIpDetail.setPeriod(TimeUnit.SECONDS);
        clientIpDetail.setModel(RuleModel.PROTO.name());
        tpsControlRule.setMonitorKeyRule(new HashMap<>());
        tpsControlRule.getMonitorKeyRule().put("clientIpSec", clientIpDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        tpsCheckRequest.setClientIp("127.0.0.1");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 5) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
                Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
            }
        }
        
        Thread.sleep(500L);
        //check event
        Assert.assertEquals(5, deniedEventCount.get());
        
    }
    
    @Test
    public void testTpsPatternMonitorOverLimit() throws InterruptedException {
        String testTpsBarrier = "test_barrier" + System.currentTimeMillis();
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<TpsRequestDeniedEvent> subscriber = new Subscriber<TpsRequestDeniedEvent>() {
            @Override
            public void onEvent(TpsRequestDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                if (testTpsBarrier.equalsIgnoreCase(event.getTpsCheckRequest().getPointName())) {
                    Assert.assertTrue(event.isMonitorModel());
                    deniedEventCount.incrementAndGet();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return TpsRequestDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 5tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(10);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        MseRuleDetail clientIpDetail = new MseRuleDetail();
        clientIpDetail.setMaxCount(5);
        clientIpDetail.setPattern("clientIp:*");
        clientIpDetail.setMonitorType(MonitorType.MONITOR.getType());
        clientIpDetail.setPeriod(TimeUnit.SECONDS);
        clientIpDetail.setModel(RuleModel.PROTO.name());
        tpsControlRule.setMonitorKeyRule(new HashMap<>());
        tpsControlRule.getMonitorKeyRule().put("clientIpSec", clientIpDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        tpsCheckRequest.setClientIp("127.0.0.1");
        for (int i = 0; i < 15; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 5) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else if (i < 10) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponse.getCode());
            }
        }
        
        Thread.sleep(1000L);
        //check event,10 for clientIpSec monitor,5 for point monitor event
        Assert.assertEquals(15, deniedEventCount.get());
        
    }
}
