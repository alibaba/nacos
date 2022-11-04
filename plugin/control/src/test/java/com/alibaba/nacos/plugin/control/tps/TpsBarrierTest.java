package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TpsBarrierTest {
    
    static {
        ControlConfigs.setINSTANCE(new ControlConfigs());
    }
    
    @Test
    public void testNormal_PointPassAndMonitorKeyDeny() {
        String testTpsBarrier = "test_barrier";
        TpsBarrier tpsBarrier = new TpsBarrier(testTpsBarrier);
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(RuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        RuleDetail monitorRuleDetail = new RuleDetail();
        monitorRuleDetail.setMaxCount(5);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(RuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("test:simple12*");
        
        Map<String, RuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
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
    public void testNormal_PointDenyAndMonitorKeyPass() {
        String testTpsBarrier = "test_barrier";
        TpsBarrier tpsBarrier = new TpsBarrier(testTpsBarrier);
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(RuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        RuleDetail monitorRuleDetail = new RuleDetail();
        monitorRuleDetail.setMaxCount(6);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(RuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("test:simple12*");
        
        Map<String, RuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        
        tpsBarrier.applyRule(tpsControlRule);
        System.out.println(JacksonUtils.toJson(tpsControlRule));
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
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
    public void testNormal_ConnectionAndClientIpMonitor() {
        String testTpsBarrier = "test_barrier";
        TpsBarrier tpsBarrier = new TpsBarrier(testTpsBarrier);
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(RuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        RuleDetail connectionIdRuleDetail = new RuleDetail();
        connectionIdRuleDetail.setMaxCount(6);
        connectionIdRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        connectionIdRuleDetail.setPeriod(TimeUnit.SECONDS);
        connectionIdRuleDetail.setModel(RuleDetail.MODEL_FUZZY);
        connectionIdRuleDetail.setPattern("connectionId:simple12*");
        RuleDetail clientIpRuleDetail = new RuleDetail();
        clientIpRuleDetail.setMaxCount(6);
        clientIpRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        clientIpRuleDetail.setPeriod(TimeUnit.SECONDS);
        clientIpRuleDetail.setModel(RuleDetail.MODEL_FUZZY);
        clientIpRuleDetail.setPattern("clientIp:127.0.0.1");
        
        Map<String, RuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_conn", connectionIdRuleDetail);
        monitorRules.put("monitorKeyRule_clientIp", clientIpRuleDetail);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        TpsCheckRequest tpsCheckRequest = new TpsCheckRequest();
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
