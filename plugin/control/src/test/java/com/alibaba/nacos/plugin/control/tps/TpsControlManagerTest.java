package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.mse.FlowedRuleDetail;
import com.alibaba.nacos.plugin.control.tps.mse.FlowedTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.plugin.control.tps.rule.RuleDetail.MODEL_FUZZY;

@RunWith(MockitoJUnitRunner.class)
public class TpsControlManagerTest {
    
    TpsControlManager tpsControlManager = new TpsControlManager();
    
    String pointName = "TEST_POINT_NAME" + System.currentTimeMillis();
    
    static {
        ControlConfigs.setInstance(new ControlConfigs());
        
    }
    
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
        RuleDetail ruleDetail = new FlowedRuleDetail();
        ruleDetail.setMaxCount(10000);
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        ruleDetail.setMonitorType(MonitorType.MONITOR.getType());
        ruleDetail.setModel(MODEL_FUZZY);
        ruleDetail.setPattern("test:prefix*");
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName(pointName);
        tpsControlRule.setPointRule(ruleDetail);
        
        FlowedRuleDetail ruleDetailMonitor = new FlowedRuleDetail();
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
        FlowedTpsCheckRequest tpsCheckRequest = new FlowedTpsCheckRequest();
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
