package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail.MODEL_FUZZY;

@RunWith(MockitoJUnitRunner.class)
public class FlowTpsControlManagerTest {
    
    TpsControlManager tpsControlManager = new MseTpsControlManager();
    
    String pointName = "TEST_POINT_NAME" + System.currentTimeMillis();
    
    @Before
    public void setUp() {
        //1.register point
        tpsControlManager.registerTpsPoint(pointName);
        Assert.assertTrue(tpsControlManager.getPoints().containsKey(pointName));
        
    }
    
    /**
     * test denied by monitor key rules.
     */
    @Test
    public void testMonitorDenyByMonitor() {
        
        tpsControlManager.applyTpsRule(pointName, null);
        //1.register rule
        MseRuleDetail ruleDetail = new MseRuleDetail();
        ruleDetail.setMaxCount(10000);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setModel(MODEL_FUZZY);
        ruleDetail.setPattern("test:prefix*");
        MseTpsControlRule tpsControlRule = new MseTpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        MseRuleDetail ruleDetailMonitor = new MseRuleDetail();
        ruleDetailMonitor.setMaxCount(5);
        ruleDetailMonitor.setMaxFlow(11);
        ruleDetailMonitor.setPeriod(TimeUnit.SECONDS);
        ruleDetailMonitor.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetailMonitor.setModel(MODEL_FUZZY);
        ruleDetailMonitor.setPattern("test:prefixmonitor*");
        tpsControlRule.getMonitorKeyRule().put("monitorkey", ruleDetailMonitor);
        
        tpsControlManager.applyTpsRule(pointName, tpsControlRule);
        Assert.assertTrue(tpsControlManager.getRules().containsKey(pointName));
        
        //3.apply tps
        MseTpsCheckRequest tpsCheckRequest = new MseTpsCheckRequest();
        tpsCheckRequest.setCount(2);
        tpsCheckRequest.setFlow(15);
        tpsCheckRequest.setPointName(pointName);
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new MonitorKey("prefixmonitor123") {
            @Override
            public String getType() {
                return "test";
            }
        });
        tpsCheckRequest.setMonitorKeys(monitorKeyList);
        
        TpsCheckResponse check = tpsControlManager.check(tpsCheckRequest);
        System.out.println(check.isSuccess() + "," + check.getMessage());
        
    }
}
