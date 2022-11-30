package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.mse.key.ClientIpMonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PointRuleMseTpsBarrierTest {
    
    @Test
    public void testNormalPointPassAndDeny() {
        String testTpsBarrier = "test_barrier";
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 6tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        // pass under 5tps
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        //deny blow 5tps
        TpsCheckResponse tpsCheckResponse0 = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse0.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse0.getCode());
        
        tpsCheckRequest.setTimestamp(tpsCheckRequest.getTimestamp() + 1000L);
        
        // next second,pass under 5tps
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        //deny blow 5tps
        TpsCheckResponse tpsCheckResponse1 = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse1.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse1.getCode());
        
    }
    
    @Test
    public void testNormalPointPassAndDenyModifyRule() {
        String testTpsBarrier = "test_barrier";
        TpsBarrier tpsBarrier = new MseTpsBarrier(testTpsBarrier);
        
        //point 6tps max
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(testTpsBarrier);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(5);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        tpsBarrier.applyRule(tpsControlRule);
        
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setPointName(testTpsBarrier);
        // pass under 5tps
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        //deny blow 5tps
        TpsCheckResponse tpsCheckResponse0 = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse0.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse0.getCode());
        
        //modify point rule,5tps to 10tps
        tpsControlRule.setPointRule(ruleDetail);
        ruleDetail.setMaxCount(10);
        tpsBarrier.applyRule(tpsControlRule);
        
        // next second,pass under 10tps
        for (int i = 0; i < 5; i++) {
            TpsCheckResponse tpsCheckResponse = tpsBarrier.applyTps(tpsCheckRequest);
            Assert.assertTrue(tpsCheckResponse.isSuccess());
            Assert.assertEquals(TpsResultCode.PASS_BY_POINT, tpsCheckResponse.getCode());
        }
        //deny blow 10tps
        TpsCheckResponse tpsCheckResponse1 = tpsBarrier.applyTps(tpsCheckRequest);
        Assert.assertFalse(tpsCheckResponse1.isSuccess());
        Assert.assertEquals(TpsResultCode.DENY_BY_POINT, tpsCheckResponse1.getCode());
        
    }
}
