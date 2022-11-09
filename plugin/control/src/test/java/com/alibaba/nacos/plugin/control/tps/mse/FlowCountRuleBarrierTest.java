package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_FUZZY;
import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_PROTO;

@RunWith(MockitoJUnitRunner.class)
public class FlowCountRuleBarrierTest {
    
    @Test
    public void testPassAndLimit() {
        
        FlowedRuleDetail flowedRuleDetail = new FlowedRuleDetail();
        flowedRuleDetail.setMaxCount(100);
        flowedRuleDetail.setMaxFlow(10);
        flowedRuleDetail.setModel(MODEL_PROTO);
        flowedRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        FlowedLocalSimpleCountRuleBarrier ruleBarrier = new FlowedLocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(flowedRuleDetail);
        
        // check pass and deny
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail.getCode() == TpsResultCode.CHECK_DENY);
        
        long timeMillis = flowedBarrierCheckRequest.getTimestamp();
        //check pass and deny next second.
        long timeMillisPlus = timeMillis + 1000L;
        flowedBarrierCheckRequest.setTimestamp(timeMillisPlus);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
            
        }
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        
    }
    
    FlowedBarrierCheckRequest createFlowedCheckRequest() {
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = new FlowedBarrierCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simpler12");
        flowedBarrierCheckRequest.setFlow(1);
        flowedBarrierCheckRequest.setMonitorKey(monitorKey);
        flowedBarrierCheckRequest.setTimestamp(System.currentTimeMillis());
        return flowedBarrierCheckRequest;
    }
    
    @Test
    public void testLimitAndRollback() {
        
        FlowedRuleDetail flowedRuleDetail = new FlowedRuleDetail();
        flowedRuleDetail.setMaxCount(100);
        flowedRuleDetail.setMaxFlow(10);
        flowedRuleDetail.setModel(MODEL_FUZZY);
        flowedRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType())
        FlowedLocalSimpleCountRuleBarrier ruleBarrier = new FlowedLocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ;
        ruleBarrier.applyRuleDetail(flowedRuleDetail);
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        // check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.CHECK_DENY);
        System.out.println(tpsCheckResponseFail2.getMessage());
        //rollback tps and check
        ruleBarrier.rollbackTps(flowedBarrierCheckRequest);
        TpsCheckResponse tpsCheckResponseSuccess3 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseSuccess3.isSuccess());
        Assert.assertTrue(tpsCheckResponseSuccess3.getCode() == TpsResultCode.CHECK_PASS);
    }
    
    @Test
    public void testPassByMonitor() {
        
        FlowedRuleDetail flowedRuleDetail = new FlowedRuleDetail();
        flowedRuleDetail.setMaxCount(100);
        flowedRuleDetail.setMaxFlow(10);
        flowedRuleDetail.setModel(MODEL_PROTO);
        flowedRuleDetail.setMonitorType(MonitorType.MONITOR.getType());
        FlowedLocalSimpleCountRuleBarrier ruleBarrier = new FlowedLocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(flowedRuleDetail);
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        TpsCheckResponse tpsCheckResponseByMonitor = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseByMonitor.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponseByMonitor.getCode());
    }
    
    @Test
    public void testModifyRule() {
        
        FlowedRuleDetail flowedRuleDetail = new FlowedRuleDetail();
        flowedRuleDetail.setMaxCount(100);
        flowedRuleDetail.setMaxFlow(10);
        flowedRuleDetail.setModel(MODEL_PROTO);
        flowedRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        FlowedLocalSimpleCountRuleBarrier ruleBarrier = new FlowedLocalSimpleCountRuleBarrier("test", "test:simple123*",
                TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(flowedRuleDetail);
        
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        //check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(flowedBarrierCheckRequest);
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
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
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
            flowedBarrierCheckRequest.setTimestamp(flowedBarrierCheckRequest.getTimestamp() + 1000);
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.CHECK_PASS);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny3 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny3.isSuccess());
        Assert.assertEquals(TpsResultCode.CHECK_DENY, tpsCheckResponseDeny3.getCode());
    }
    
}
