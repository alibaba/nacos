package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail.MODEL_FUZZY;

public class TpsInterceptorTest {
    
    @Before
    public void setUp() {
    
    }
    
    /**
     * testPassByMonitor.
     */
    @Test
    public void testPassByPreInterceptor() {
        String pointName = "interceptortest";
        
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(1);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(1);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("clientIp:*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(pointName);
        tpsBarrier.applyRule(tpsControlRule);
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setClientIp("127.0.0.1_pre");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse check = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(check.isSuccess());
            Assert.assertEquals(MseTpsResultCode.PASS_BY_PRE_INTERCEPTOR, check.getCode());
        }
        
    }
    
    /**
     * testPassByMonitor.
     */
    @Test
    public void testDenyByPreInterceptor() {
        String pointName = "interceptortest";
        
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(10);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(10);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("clientIp:*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(pointName);
        tpsBarrier.applyRule(tpsControlRule);
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setClientIp("127.0.0.1_pre_black");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse check = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(check.isSuccess());
            Assert.assertEquals(MseTpsResultCode.PASS_BY_PRE_INTERCEPTOR, check.getCode());
        }
        
    }
    
    /**
     * testPassByMonitor.
     */
    @Test
    public void testForbiddenInterceptor() {
        String pointName = "interceptortest";
        
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(10);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setDisabledInterceptors(Sets.newHashSet("testnacosinter"));
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(10);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("clientIp:*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(pointName);
        tpsBarrier.applyRule(tpsControlRule);
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setClientIp("127.0.0.1_pre_black");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse check = tpsBarrier.applyTps(tpsCheckRequest);
            if (i < 5) {
                Assert.assertTrue(check.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_POINT, check.getCode());
            } else {
                Assert.assertTrue(check.isSuccess());
                Assert.assertEquals(TpsResultCode.PASS_BY_MONITOR, check.getCode());
            }
            
        }
        
    }
    
    /**
     * testPassByMonitor.
     */
    @Test
    public void testPassByPostInterceptor() {
        String pointName = "interceptortest";
        
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(0);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(0);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("clientIp:*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(pointName);
        tpsBarrier.applyRule(tpsControlRule);
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setClientIp("127.0.0.1_post");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse check = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(check.isSuccess());
            Assert.assertEquals(MseTpsResultCode.PASS_BY_POST_INTERCEPTOR, check.getCode());
        }
        
        
    }
    
    /**
     * testPassByMonitor.
     */
    @Test
    public void testDenyByPostInterceptor() {
        String pointName = "interceptortest";
        
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(1000);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(100);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("clientIp:*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        MseTpsBarrier tpsBarrier = new MseTpsBarrier(pointName);
        tpsBarrier.applyRule(tpsControlRule);
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setPointName(pointName);
        tpsCheckRequest.setClientIp("127.0.0.1_post_black");
        for (int i = 0; i < 10; i++) {
            TpsCheckResponse check = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertFalse(check.isSuccess());
            Assert.assertEquals(MseTpsResultCode.DENY_BY_POST_INTERCEPTOR, check.getCode());
        }
        
        
    }
}
