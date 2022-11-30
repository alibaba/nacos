package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.RuleBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class MseTpsControlManagerTest {
    
    @Test
    public void test() throws InterruptedException {
        String pointName = "test_pointername";
        
        //register rule.
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(6);
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        MseRuleDetail clientIpDetail = new MseRuleDetail();
        clientIpDetail.setMaxCount(6);
        clientIpDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        clientIpDetail.setPeriod(TimeUnit.SECONDS);
        clientIpDetail.setPattern("clientIp:*");
        clientIpDetail.setModel(RuleModel.PROTO.name());
        clientIpDetail.setPrintLog(true);
        tpsControlRule.setMonitorKeyRule(new HashMap<>());
        tpsControlRule.getMonitorKeyRule().put("clientIp", clientIpDetail);
        
        //register point.
        MseTpsControlManager mseTpsControlManager = new MseTpsControlManager();
        mseTpsControlManager.registerTpsPoint(pointName);
        mseTpsControlManager.applyTpsRule(pointName, tpsControlRule);
        Assert.assertTrue(mseTpsControlManager.getPoints().containsKey(pointName));
        Assert.assertTrue(mseTpsControlManager.getRules().containsKey(pointName));
        RuleBarrier pointBarrier = mseTpsControlManager.getPoints().get(pointName).getPointBarrier();
        
        Assert.assertEquals(6, pointBarrier.getMaxCount());
        Assert.assertEquals(MonitorType.INTERCEPT.getType(), pointBarrier.getMonitorType());
        Assert.assertEquals(TimeUnit.SECONDS, pointBarrier.getPeriod());
        MseTpsCheckRequest mseTpsCheckRequest = new MseTpsCheckRequest(pointName, null, "127.0.0.1");
        mseTpsControlManager.check(mseTpsCheckRequest);
        Thread.sleep(5000L);
        
    }
}
