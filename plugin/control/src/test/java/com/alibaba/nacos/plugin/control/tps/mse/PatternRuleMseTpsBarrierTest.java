package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.mse.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.ConnectionIdMonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PatternRuleMseTpsBarrierTest {
    
    @Test
    public void testPointPassButExactClientIpDeny() {
        ControlConfigs.getInstance().setRuleParser("mse");
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        //point 5 tps
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        //client ip,10 tps
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(10);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("clientIp:127.0.0.1");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        ClientIpMonitorKey monitorKey = new ClientIpMonitorKey();
        monitorKey.setKey("127.0.0.1");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(MseTpsResultCode.PASS_BY_PATTERN, tpsCheckResponse.getCode());
            
        }
        
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse.isSuccess());
        Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
    }
    
    @Test
    public void testPointPassButExactConnectionIdDeny() {
        ControlConfigs.getInstance().setRuleParser("mse");
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        //point 5 tps
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        //client ip,10 tps
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(10);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("connectionId:test-connection123");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        ConnectionIdMonitorKey monitorKey = new ConnectionIdMonitorKey();
        monitorKey.setKey("test-connection123");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(MseTpsResultCode.PASS_BY_PATTERN, tpsCheckResponse.getCode());
            
        }
        
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse.isSuccess());
        Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
    }
    
    @Test
    public void testPatternPassButPointDeny() {
        ControlConfigs.getInstance().setRuleParser("mse");
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        //point 5 tps
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        //connectionId ,10 tps
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(10);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        monitorRuleDetail.setPattern("connectionId:*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        ConnectionIdMonitorKey monitorKey = new ConnectionIdMonitorKey();
        monitorKey.setKey("test-connection123");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
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
        
        //check metrics, pass 5, deny 5.
        MseTpsMetrics metrics = (MseTpsMetrics) tpsBarrier.getPointBarrier().getMetrics(timeMillis);
        System.out.println(metrics);
        MseTpsMetrics metricsPattern = (MseTpsMetrics) tpsBarrier.patternBarriers.iterator().next()
                .getMetrics(timeMillis);
        System.out.println(metricsPattern);
        
    }
    
    
    @Test
    public void testPointPassButFuzzyClientIpDeny() {
        ControlConfigs.getInstance().setRuleParser("mse");
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        //point 5 tps
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        //client ip,4 tps，fuzzy match
        MseRuleDetail monitorRuleDetail = new MseRuleDetail();
        monitorRuleDetail.setMaxCount(4);
        monitorRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        monitorRuleDetail.setPeriod(TimeUnit.SECONDS);
        monitorRuleDetail.setPattern("clientIp:*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule", monitorRuleDetail);
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        ClientIpMonitorKey monitorKey = new ClientIpMonitorKey();
        monitorKey.setKey("127.0.0.1");
        tpsCheckRequest.setMonitorKeys(new ArrayList<>(CollectionUtils.list(monitorKey)));
        tpsCheckRequest.setTimestamp(timeMillis);
        
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
                Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse.getCode());
            }
        }
        // update client ip 4 tps to 5 tps.
        monitorRuleDetail.setMaxCount(5);
        tpsBarrier.applyRule(tpsControlRule);
        TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertTrue(tpsCheckResponse.isSuccess());
        Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        
        TpsCheckResponse tpsCheckResponse2 = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse2.isSuccess());
        Assert.assertEquals(MseTpsResultCode.DENY_BY_PATTERN, tpsCheckResponse2.getCode());
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
    
    /**
     * test exact match with order.
     */
    @Test
    public void testExactConnectionAndClientIpPatternWithOrder() {
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
        connectionIdRuleDetail.setMaxCount(4);
        connectionIdRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        connectionIdRuleDetail.setPeriod(TimeUnit.SECONDS);
        connectionIdRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        connectionIdRuleDetail.setOrder(1);
        connectionIdRuleDetail.setPattern("connectionId:simple123");
        
        MseRuleDetail clientIpRuleDetail = new MseRuleDetail();
        clientIpRuleDetail.setMaxCount(3);
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
        tpsCheckRequest.setTimestamp(timeMillis);
        
        //deny by connectionIdRuleDetail 4tps.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
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
            if (i < 3) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
    }
    
    /**
     * test exact match with order.
     */
    @Test
    public void testExactuserDefinePatternWithOrder() {
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail userDefineRuleDetail = new MseRuleDetail();
        userDefineRuleDetail.setMaxCount(4);
        userDefineRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        userDefineRuleDetail.setPeriod(TimeUnit.SECONDS);
        userDefineRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        userDefineRuleDetail.setOrder(1);
        userDefineRuleDetail.setPattern("userDefine1:simple123");
        
        MseRuleDetail userdefineRuleDetail2 = new MseRuleDetail();
        userdefineRuleDetail2.setMaxCount(3);
        userdefineRuleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        userdefineRuleDetail2.setPeriod(TimeUnit.SECONDS);
        userdefineRuleDetail2.setOrder(3);
        userdefineRuleDetail2.setModel(MseRuleDetail.MODEL_FUZZY);
        userdefineRuleDetail2.setPattern("userDefine2:127.0.0.1");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_conn", userDefineRuleDetail);
        monitorRules.put("monitorKeyRule_clientIp", userdefineRuleDetail2);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        //connectionId has high priority
        tpsBarrier.applyRule(tpsControlRule);
        
        //test point keys
        long timeMillis = System.currentTimeMillis();
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setMonitorKeys(new ArrayList<>());
        tpsCheckRequest.getMonitorKeys().add(new MonitorKey("simple123") {
            @Override
            public String getType() {
                return "userDefine1";
            }
        });
        tpsCheckRequest.getMonitorKeys().add(new MonitorKey("127.0.0.1") {
            @Override
            public String getType() {
                return "userDefine2";
            }
        });
        tpsCheckRequest.setTimestamp(timeMillis);
        
        //deny by connectionIdRuleDetail 4tps.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
        //clientIp has high priority
        userDefineRuleDetail.setOrder(4);
        userdefineRuleDetail2.setOrder(1);
        tpsBarrier.applyRule(tpsControlRule);
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        //deny by clientIp 3tps.
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 3) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
    }
    
    /**
     * testProtoAndFuzzyModel.
     */
    @Test
    public void testProtoModel() {
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail userDefineRuleDetail = new MseRuleDetail();
        userDefineRuleDetail.setMaxCount(4);
        userDefineRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        userDefineRuleDetail.setPeriod(TimeUnit.SECONDS);
        userDefineRuleDetail.setModel(MseRuleDetail.MODEL_PROTO);
        userDefineRuleDetail.setOrder(1);
        userDefineRuleDetail.setPattern("clientIp:*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_userdefine", userDefineRuleDetail);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        long timeMillis = System.currentTimeMillis();
        //different keys,should pass.
        for (int i = 0; i < 6; i++) {
            MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
            tpsCheckRequest.setTimestamp(timeMillis);
            tpsCheckRequest.setClientIp("127.0.0.1_" + i);
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
        }
        //total over 6tps ,denied.
        MseTpsCheckRequest tpsCheckRequest0 = new MseTpsCheckRequest();
        tpsCheckRequest0.setTimestamp(timeMillis);
        tpsCheckRequest0.setClientIp("127.0.0.1_");
        TpsCheckResponse tpsCheckResponse0 = tpsBarrier.applyTps(tpsCheckRequest0);
        Assert.assertFalse(tpsCheckResponse0.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse0.getCode());
        
        //same keys.
        for (int i = 0; i < 5; i++) {
            MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
            tpsCheckRequest.setTimestamp(timeMillis + 1000L);
            tpsCheckRequest.setClientIp("127.0.0.1");
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
            }
        }
        
        //check proto metrics.
        MseRuleBarrier patternBarrier = tpsBarrier.patternBarriers.iterator().next();
        
        MseTpsMetrics metrics = (MseTpsMetrics) patternBarrier.getMetrics(timeMillis);
        TpsMetrics.Counter counter = metrics.getCounter();
        Assert.assertTrue(counter.getPassCount() == 6);
        Assert.assertTrue(counter.getDeniedCount() == 0);
        
        for (int i = 0; i < 6; i++) {
            String clientIp = "127.0.0.1_" + i;
            TpsMetrics.Counter counter1 = metrics.getProtoKeyCounter().get(clientIp);
            Assert.assertTrue(counter1.getPassCount() == 1);
            Assert.assertTrue(counter1.getDeniedCount() == 0);
        }
        
        MseTpsMetrics metrics2 = (MseTpsMetrics) patternBarrier.getMetrics(timeMillis + 1000L);
        TpsMetrics.Counter counter2 = metrics2.getCounter();
        Assert.assertTrue(counter2.getPassCount() == 4);
        Assert.assertTrue(counter2.getDeniedCount() == 1);
        
        String clientIp = "127.0.0.1";
        TpsMetrics.Counter counter11 = metrics2.getProtoKeyCounter().get(clientIp);
        Assert.assertTrue(counter11.getPassCount() == 4);
        Assert.assertTrue(counter11.getDeniedCount() == 1);
        
    }
    
    /**
     * testProtoAndFuzzyModel.
     */
    @Test
    public void testFuzzyModel() {
        String testTpsBarrier = "test_barrier";
        
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail userDefineRuleDetail = new MseRuleDetail();
        userDefineRuleDetail.setMaxCount(4);
        userDefineRuleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        userDefineRuleDetail.setPeriod(TimeUnit.SECONDS);
        userDefineRuleDetail.setModel(MseRuleDetail.MODEL_FUZZY);
        userDefineRuleDetail.setOrder(1);
        userDefineRuleDetail.setPattern("clientIp:*");
        
        Map<String, MseRuleDetail> monitorRules = new HashMap<>();
        monitorRules.put("monitorKeyRule_userdefine", userDefineRuleDetail);
        
        tpsControlRule.setMonitorKeyRule(monitorRules);
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        tpsBarrier.applyRule(tpsControlRule);
        
        long timeMillis = System.currentTimeMillis();
        //different keys,should pass.
        for (int i = 0; i < 6; i++) {
            MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
            tpsCheckRequest.setTimestamp(timeMillis);
            tpsCheckRequest.setClientIp("127.0.0.1_" + i);
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 4) {
                Assert.assertTrue(tpsCheckResponse.isSuccess());
            } else {
                Assert.assertFalse(tpsCheckResponse.isSuccess());
                
            }
        }
        
    }
}

