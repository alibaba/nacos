package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FlowRuleMseTpsBarrierTest {
    
    @Test
    public void testTpsPassButFlowDenyExactMatch() {
        ControlConfigs.getInstance().setRuleParser("mse");
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        //point 5 tps,100 fps
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMaxFlow(100);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        //client ip,10 tps,50 fps.
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(10);
        monitorRuleDetail.setMaxFlow(50);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("clientIp:127.0.0.1");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setClientIp("127.0.0.1");
        tpsCheckRequest.setTimestamp(timeMillis);
        tpsCheckRequest.setFlow(20);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 2) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(MseTpsResultCode.PASS_BY_PATTERN, tpsCheckResponse.getCode());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
                Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
            }
            
        }
        
        //get metrics.
        MseTpsMetrics pointMetrics = (MseTpsMetrics) tpsBarrier.getPointBarrier().getMetrics(timeMillis);
        MseTpsMetrics patternMetrics = (MseTpsMetrics) tpsBarrier.patternBarriers.iterator().next().getMetrics(timeMillis);
        Assert.assertEquals(2,pointMetrics.getCounter().getPassCount());
        Assert.assertEquals(0,pointMetrics.getCounter().getDeniedCount());
    
        Assert.assertEquals(2,((FlowCounter)patternMetrics.getCounter()).getPassCount());
        Assert.assertEquals(0,((FlowCounter)patternMetrics.getCounter()).getDeniedCount());
        
        Assert.assertEquals(40,((FlowCounter)patternMetrics.getCounter()).getPassFlow());
        Assert.assertEquals(160,((FlowCounter)patternMetrics.getCounter()).getDeniedFlow());
        
    }
    
    /**
     * test exact match with order.
     */
    @Test
    public void testFlowExactConnectionAndClientIpPatternWithOrder() {
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMaxFlow(40);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail connectionIdRuleDetail = new MseRuleDetail();
        connectionIdRuleDetail.setMaxCount(4);
        connectionIdRuleDetail.setMaxFlow(30);
        connectionIdRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        connectionIdRuleDetail.setPeriod(TimeUnit.SECONDS);
        connectionIdRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        connectionIdRuleDetail.setOrder(1);
        connectionIdRuleDetail.setPattern("connectionId:simple123");
        
        MseRuleDetail clientIpRuleDetail = new MseRuleDetail();
        clientIpRuleDetail.setMaxCount(6);
        clientIpRuleDetail.setMaxFlow(40);
        clientIpRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        clientIpRuleDetail.setPeriod(TimeUnit.SECONDS);
        clientIpRuleDetail.setOrder(3);
        clientIpRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        clientIpRuleDetail.setPattern("clientIp:127.0.0.1");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_conn", connectionIdRuleDetail);
        monitorRules.put("monitorKeyRule_clientIp", clientIpRuleDetail);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        //connectionId has high priority
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setConnectionId("simple123");
        tpsCheckRequest.setClientIp("127.0.0.1");
        tpsCheckRequest.setFlow(10);
        tpsCheckRequest.setTimestamp(timeMillis);
        
        //deny by connectionIdRuleDetail 30 fps.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 3) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
        //clientIp has high priority
        connectionIdRuleDetail.setOrder(4);
        clientIpRuleDetail.setOrder(1);
        tpsBarrier.applyRule(tpsControlRule);
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        //deny by clientIp 3tps.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
    }
    
    
}

