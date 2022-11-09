package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.BarrierCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_FUZZY;
import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_PROTO;

@RunWith(MockitoJUnitRunner.class)
public class LocalSimpleCountRuleBarrierTest {
    
    @Test
    public void testPassAndLimit() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        // check pass and deny
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail.getCode() == TpsResultCode.CHECK_DENY);
        
        //check pass and deny next second.
        long timeMillisPlus = timeMillis + 1000L;
        barrierCheckRequest.setTimestamp(timeMillisPlus);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        
    }
    
    @Test
    public void testLimitAndRollback() {
        SimpleCountRuleBarrier ruleBarrier = (SimpleCountRuleBarrier) LocalSimpleCountBarrierCreator.getInstance()
                .createRuleBarrier("test", "test:simple123*", TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_FUZZY);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        
        // check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        
        //rollback tps and check
        ruleBarrier.rollbackTps(barrierCheckRequest);
        TpsCheckResponse tpsCheckResponseSuccess3 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseSuccess3.isSuccess());
        Assert.assertTrue(tpsCheckResponseSuccess3.getCode() == TpsResultCode.CHECK_PASS);
    }
    
    @Test
    public void testPassByMonitor() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_FUZZY);
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
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        TpsCheckResponse tpsCheckResponseByMonitor = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseByMonitor.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponseByMonitor.getCode());
    }
    
    @Test
    public void testFuzzyModel() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, MODEL_FUZZY);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_FUZZY);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        
        //check different keys pass
        for (int i = 0; i < 10; i++) {
            monitorKey.setKey("simpler12" + i);
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        
        //check deny
        monitorKey.setKey("simpler12" + System.currentTimeMillis());
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny.getCode());
        ruleBarrier.setModel(MODEL_PROTO);
    }
    
    @Test
    public void testProtoModel() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, MODEL_PROTO);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        
        //check different keys pass
        for (int i = 0; i < 20; i++) {
            monitorKey.setKey("simpler12" + i);
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        
        //check same key
        barrierCheckRequest.setTimestamp(barrierCheckRequest.getTimestamp() + 1000);
        for (int i = 0; i < 10; i++) {
            monitorKey.setKey("simpler12");
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny.getCode());
        ruleBarrier.setModel(MODEL_PROTO);
    }
    
    @Test
    public void testModifyRule() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(10);
        ruleBarrier.setModel(MODEL_FUZZY);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        BarrierCheckRequest barrierCheckRequest = new BarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        barrierCheckRequest.setMonitorKey(monitorKey);
        barrierCheckRequest.setTimestamp(timeMillis);
        
        //check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny.getCode());
        
        //apply new rule,add max count
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(15);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(ruleBarrier.getPeriod());
        ruleDetail.setModel(ruleBarrier.getModel());
        ruleBarrier.applyRuleDetail(ruleDetail);
        //check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny2 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny2.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny2.getCode());
        
        //apply new rule,modify period
        RuleDetail ruleDetail2 = new RuleDetail();
        ruleDetail2.setMaxCount(15);
        ruleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail2.setPeriod(TimeUnit.MINUTES);
        ruleDetail2.setModel(ruleBarrier.getModel());
        ruleBarrier.applyRuleDetail(ruleDetail2);
        //check pass
        for (int i = 0; i < 15; i++) {
            barrierCheckRequest.setTimestamp(barrierCheckRequest.getTimestamp() + 1000);
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check pass
        TpsCheckResponse tpsCheckResponseDeny3 = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny3.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny3.getCode());
    }
    
}
