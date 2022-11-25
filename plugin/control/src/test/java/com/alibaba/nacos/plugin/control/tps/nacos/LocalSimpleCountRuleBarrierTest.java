package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class LocalSimpleCountRuleBarrierTest {
    
    @Test
    public void testPassAndLimit() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        // check pass and deny
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        barrierCheckRequest.setTimestamp(timeMillis);
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
            
        }
        TpsCheckResponse tpsCheckResponseFail = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail.getCode() == TpsResultCode.DENY_BY_POINT);
        
        //check pass and deny next second.
        long timeMillisPlus = timeMillis + 1000L;
        barrierCheckRequest.setTimestamp(timeMillisPlus);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
            
        }
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.DENY_BY_POINT);
        
    }
    
    @Test
    public void testLimitAndRollback() {
        SimpleCountRuleBarrier ruleBarrier = (SimpleCountRuleBarrier) LocalSimpleCountBarrierCreator.getInstance()
                .createRuleBarrier("test", "test", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        barrierCheckRequest.setTimestamp(timeMillis);
        
        // check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.DENY_BY_POINT);
        
        //rollback tps and check
        ruleBarrier.rollbackTps(barrierCheckRequest);
        TpsCheckResponse tpsCheckResponseSuccess3 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseSuccess3.isSuccess());
        Assert.assertTrue(tpsCheckResponseSuccess3.getCode() == TpsResultCode.PASS_BY_POINT);
    }
    
    @Test
    public void testPassByMonitor() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setMonitorType(MonitorType.MONITOR.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        TpsCheckResponse tpsCheckResponseByMonitor = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseByMonitor.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponseByMonitor.getCode());
    }
    
    
}
