package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_PROTO;

@RunWith(MockitoJUnitRunner.class)
public class FlowCountRuleBarrierTest {
    
    
    @Test
    public void testPassAndLimit() {
        FlowedCountRuleBarrier ruleBarrier = new FlowedCountRuleBarrier("test", "test:simple123*", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(100);
        ruleBarrier.setMaxFlow(10);
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        // check pass and deny
        long timeMillis = System.currentTimeMillis();
        FlowedTpsCheckRequest tpsCheckRequest = new FlowedTpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        tpsCheckRequest.setFlow(1);
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail.getCode() == TpsResultCode.CHECK_DENY);
        
        //check pass and deny next second.
        long timeMillisPlus = timeMillis + 1000l;
        tpsCheckRequest.setTimestamp(timeMillisPlus);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        
    }
    
    
    @Test
    public void testLimitAndRollback() {
        FlowedCountRuleBarrier ruleBarrier = new FlowedCountRuleBarrier("test", "test:simple123*", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(100);
        ruleBarrier.setMaxFlow(10);
    
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
        // check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        
        //rollback tps and check
        ruleBarrier.rollbackTps(tpsCheckRequest);
        TpsCheckResponse tpsCheckResponseSuccess3 = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertTrue(tpsCheckResponseSuccess3.isSuccess());
        Assert.assertTrue(tpsCheckResponseSuccess3.getCode() == TpsResultCode.CHECK_PASS);
    }
    
    @Test
    public void testPassByMonitor() {
        FlowedCountRuleBarrier ruleBarrier = new FlowedCountRuleBarrier("test", "test:simple123*", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(100);
        ruleBarrier.setMaxFlow(10);
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.MONITOR.getType());
        
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        TpsCheckResponse tpsCheckResponseByMonitor = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertTrue(tpsCheckResponseByMonitor.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponseByMonitor.getCode());
    }
    
    @Test
    public void testModifyRule() {
        FlowedCountRuleBarrier ruleBarrier = new FlowedCountRuleBarrier("test", "test:simple123*", TimeUnit.SECONDS);
        ruleBarrier.setMaxCount(100);
        ruleBarrier.setMaxFlow(10);
        ruleBarrier.setModel(MODEL_PROTO);
        ruleBarrier.setMonitorType(MonitorType.INTERCEPT.getType());
        
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        //check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny.getCode());
        
        //apply new rule,add max count
        FlowedRuleDetail ruleDetail = new FlowedRuleDetail();
        ruleDetail.setMaxFlow(15);
        ruleDetail.setMaxCount(100);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(ruleBarrier.getPeriod());
        ruleDetail.setModel(ruleBarrier.getModel());
        ruleBarrier.applyRuleDetail(ruleDetail);
        //check pass
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny2 = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny2.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny2.getCode());
        
        //apply new rule,modify period
        FlowedRuleDetail ruleDetail2 = new FlowedRuleDetail();
        ruleDetail2.setMaxFlow(15);
        ruleDetail2.setMaxCount(100);
        ruleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail2.setPeriod(TimeUnit.MINUTES);
        ruleDetail2.setModel(ruleBarrier.getModel());
        ruleBarrier.applyRuleDetail(ruleDetail2);
        //check pass
        for (int i = 0; i < 15; i++) {
            tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000);
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check pass
        TpsCheckResponse tpsCheckResponseDeny3 = ruleBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny3.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny3.getCode());
    }
    
}
