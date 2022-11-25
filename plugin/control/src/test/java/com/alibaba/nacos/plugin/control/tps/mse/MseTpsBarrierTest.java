package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MseTpsBarrierTest {
    
    @Test
    public void testNormalPointPassAndMonitorKeyDeny() {
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
    
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(5);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("test:simple12*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simple12");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse.isSuccess());
        System.out.println(tpsCheckResponse.getMessage());
        
    }
    
    @Test
    public void testNormalPointDenyAndMonitorKeyPass() {
        String testTpsBarrier = "interceptortest";
    
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
    
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(6);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("test:simple12*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        System.out.println(JacksonUtils.toJson(tpsControlRule));
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        MonitorKey monitorKey = new MonitorKey() {
            @Override
            public String getType() {
                return "test";
            }
        };
        monitorKey.setKey("simple12");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        tpsCheckRequest.setPointName("interceptortest");
        
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse.isSuccess());
        System.out.println(tpsCheckResponse.getMessage());
        
    }
    
    @Test
    public void testNormalConnectionAndClientIpMonitor() {
        String testTpsBarrier = "test_barrier";
    
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
    
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
    
        MseRuleDetail connectionIdRuleDetail = new MseRuleDetail();
        connectionIdRuleDetail.setMaxCount(5);
        connectionIdRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        connectionIdRuleDetail.setPeriod(TimeUnit.SECONDS);
        connectionIdRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        connectionIdRuleDetail.setPattern("connectionId:simple12*");
        MseRuleDetail clientIpRuleDetail = new MseRuleDetail();
        clientIpRuleDetail.setMaxCount(5);
        clientIpRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        clientIpRuleDetail.setPeriod(TimeUnit.SECONDS);
        clientIpRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        clientIpRuleDetail.setPattern("clientIp:127.0.0.1");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_conn", connectionIdRuleDetail);
        monitorRules.put("monitorKeyRule_clientIp", clientIpRuleDetail);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setConnectionId("simple123");
        tpsCheckRequest.setClientIp("127.0.0.1");
        tpsCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse.isSuccess());
        System.out.println(tpsCheckResponse.getMessage());
        
    }
}
