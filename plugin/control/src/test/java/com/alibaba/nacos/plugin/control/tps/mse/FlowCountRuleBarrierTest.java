package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.nacos.LocalSimpleCountRuleBarrier;
import com.alibaba.nacos.plugin.control.tps.nacos.SimpleCountRuleBarrier;
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

import static com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail.MODEL_FUZZY;
import static com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail.MODEL_PROTO;

@RunWith(MockitoJUnitRunner.class)
public class FlowCountRuleBarrierTest {
    
    @Test
    public void testPassAndLimit() {
        
        MseRuleDetail mseRuleDetail = new MseRuleDetail();
        mseRuleDetail.setMaxCount(100);
        mseRuleDetail.setMaxFlow(10);
        mseRuleDetail.setModel(MODEL_PROTO);
        mseRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseRuleBarrier ruleBarrier = new MseRuleBarrier("test", "test",
                "test:simple123*", TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(mseRuleDetail);
        
        // check pass and deny
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
            
        }
        TpsCheckResponse tpsCheckResponseFail = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail.getCode() == TpsResultCode.DENY_BY_POINT);
        
        long timeMillis = flowedBarrierCheckRequest.getTimestamp();
        //check pass and deny next second.
        long timeMillisPlus = timeMillis + 1000L;
        flowedBarrierCheckRequest.setTimestamp(timeMillisPlus);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
            
        }
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.DENY_BY_POINT);
        
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
        
        MseRuleDetail mseRuleDetail = new MseRuleDetail();
        mseRuleDetail.setMaxCount(100);
        mseRuleDetail.setMaxFlow(10);
        mseRuleDetail.setModel(MODEL_FUZZY);
        mseRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseRuleBarrier ruleBarrier = new MseRuleBarrier("test", "test",
                "test:simple123*", TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ;
        ruleBarrier.applyRuleDetail(mseRuleDetail);
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        // check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseFail2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseFail2.isSuccess());
        Assert.assertTrue(tpsCheckResponseFail2.getCode() == TpsResultCode.DENY_BY_POINT);
        System.out.println(tpsCheckResponseFail2.getMessage());
        //rollback tps and check
        ruleBarrier.rollbackTps(flowedBarrierCheckRequest);
        TpsCheckResponse tpsCheckResponseSuccess3 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseSuccess3.isSuccess());
        Assert.assertTrue(tpsCheckResponseSuccess3.getCode() == TpsResultCode.PASS_BY_POINT);
    }
    
    @Test
    public void testPassByMonitor() {
        
        MseRuleDetail mseRuleDetail = new MseRuleDetail();
        mseRuleDetail.setMaxCount(100);
        mseRuleDetail.setMaxFlow(10);
        mseRuleDetail.setModel(MODEL_PROTO);
        mseRuleDetail.setMonitorType(MonitorType.MONITOR.getType());
        MseRuleBarrier ruleBarrier = new MseRuleBarrier("test", "test",
                "test:simple123*", TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(mseRuleDetail);
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        TpsCheckResponse tpsCheckResponseByMonitor = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertTrue(tpsCheckResponseByMonitor.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, tpsCheckResponseByMonitor.getCode());
    }
    
    @Test
    public void testModifyRule() {
        
        MseRuleDetail mseRuleDetail = new MseRuleDetail();
        mseRuleDetail.setMaxCount(100);
        mseRuleDetail.setMaxFlow(10);
        mseRuleDetail.setModel(MODEL_PROTO);
        mseRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseRuleBarrier ruleBarrier = new MseRuleBarrier("test", "test",
                "test:simple123*", TimeUnit.SECONDS, RuleModel.FUZZY.name());
        ruleBarrier.applyRuleDetail(mseRuleDetail);
        
        FlowedBarrierCheckRequest flowedBarrierCheckRequest = createFlowedCheckRequest();
        
        //check pass
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponseDeny.getCode());
        
        //apply new rule,add max count
        MseRuleDetail ruleDetail = new MseRuleDetail();
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
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny2 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny2.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponseDeny2.getCode());
        
        //apply new rule,modify period
        MseRuleDetail ruleDetail2 = new MseRuleDetail();
        ruleDetail2.setMaxFlow(15);
        ruleDetail2.setMaxCount(100);
        ruleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail2.setPeriod(TimeUnit.MINUTES);
        ruleDetail2.setModel(ruleBarrier.getModel());
        ruleBarrier.applyRuleDetail(ruleDetail2);
        //check pass
        for (int i = 0; i < 15; i++) {
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(flowedBarrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny3 = ruleBarrier.applyTps(flowedBarrierCheckRequest);
        TpsMetrics metrics = ruleBarrier.getMetrics(flowedBarrierCheckRequest.getTimestamp());
        System.out.println(tpsCheckResponseDeny3.getMessage());
        if (tpsCheckResponseDeny3.isSuccess()) {
            System.out.println(metrics);
        }
        Assert.assertFalse(tpsCheckResponseDeny3.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponseDeny3.getCode());
    }
    
    @Test
    public void testFuzzyModel() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test", "test:simple123*",
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
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        
        //check deny
        monitorKey.setKey("simpler12" + System.currentTimeMillis());
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponseDeny.getCode());
        ruleBarrier.setModel(MODEL_PROTO);
    }
    
    @Test
    public void testProtoModel() {
        SimpleCountRuleBarrier ruleBarrier = new LocalSimpleCountRuleBarrier("test", "test", "test:simple123*",
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
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        
        //check same key
        barrierCheckRequest.setTimestamp(barrierCheckRequest.getTimestamp() + 1000);
        for (int i = 0; i < 10; i++) {
            monitorKey.setKey("simpler12");
            TpsCheckResponse tpsCheckResponse = ruleBarrier.applyTps(barrierCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertTrue(tpsCheckResponse.getCode() == TpsResultCode.PASS_BY_POINT);
        }
        //check deny
        TpsCheckResponse tpsCheckResponseDeny = ruleBarrier.applyTps(barrierCheckRequest);
        Assert.assertFalse(tpsCheckResponseDeny.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponseDeny.getCode());
        ruleBarrier.setModel(MODEL_PROTO);
    }
    
}
