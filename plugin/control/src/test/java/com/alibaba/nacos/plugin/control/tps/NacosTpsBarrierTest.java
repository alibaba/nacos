package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.nacos.NacosTpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class NacosTpsBarrierTest {
    
    RuleBarrierCreator before;
    
    @Before
    public void setUp() {
        before = TpsBarrier.ruleBarrierCreator;
        TpsBarrier.ruleBarrierCreator = new LocalSimpleCountBarrierCreator();
    }
    
    @After
    public void after() {
        TpsBarrier.ruleBarrierCreator = before;
    }
    
    @Test
    public void testNormalPointPassAndDeny() {
        String testTpsBarrier = "test_barrier";
        
        // max 5tps
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        TpsBarrier tpsBarrier = new NacosTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setTimestamp(timeMillis);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        
        // 10tps check deny
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(tpsCheckResponse.isSuccess());
        }
        //check metrics
        TpsMetrics metrics = tpsBarrier.getPointBarrier().getMetrics(tpsCheckRequest.getTimestamp());
        Assert.assertEquals(metrics.getCounter().getPassCount(), 5);
        Assert.assertEquals(metrics.getCounter().getDeniedCount(), 5);
        
        //modify rule,5tps to 10tps
        ruleDetail.setMaxCount(10);
        tpsControlRule.setPointRule(ruleDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        // 10tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        
        // 15tps check deny
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(tpsCheckResponse.isSuccess());
        }
        
        //check metrics.
        metrics = tpsBarrier.getPointBarrier().getMetrics(tpsCheckRequest.getTimestamp());
        Assert.assertEquals(metrics.getCounter().getPassCount(), 10);
        Assert.assertEquals(metrics.getCounter().getDeniedCount(), 10);
        
    }
    
    @Test
    public void testNormalMonitorType() {
        String testTpsBarrier = "test_barrier";
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        TpsBarrier tpsBarrier = new NacosTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setTimestamp(timeMillis);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            
        }
        
        // 5tps check pass by monitor
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponse.getCode());
        }
        
    }
    
    @Test
    public void testNormalMinutesDeny() {
        String testTpsBarrier = "test_barrier";
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        TpsBarrier tpsBarrier = new NacosTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setTimestamp(timeMillis);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            
        }
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        // next 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        
        //modify to minute limit
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.MINUTES);
        tpsBarrier.applyRule(tpsControlRule);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            
        }
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        // next 5tps check deny.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse.getCode());
        }
        
    }
    
    @Test
    public void testNormalHourDeny() {
        String testTpsBarrier = "test_barrier";
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        TpsBarrier tpsBarrier = new NacosTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        tpsCheckRequest.setTimestamp(timeMillis);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            
        }
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        // next 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        
        //modify to hour limit
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.HOURS);
        tpsBarrier.applyRule(tpsControlRule);
        
        // 5tps check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        
        // 5tps check deny
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse.getCode());
        }
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L * 60 * 60);
        // next hours 5tps check pass.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        
        // next hours 5tps check deny.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse.getCode());
        }
        
    }
}
