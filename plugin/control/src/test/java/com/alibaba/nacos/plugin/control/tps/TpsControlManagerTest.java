package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.nacos.NacosTpsControlManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TpsControlManagerTest {
    
    TpsControlManager tpsControlManager = new NacosTpsControlManager();
    
    String pointName = "TEST_POINT_NAME" + System.currentTimeMillis();
    
    @Before
    public void setUp() {
        ControlConfigs.getInstance().setTpsEnabled(true);
        //1.register point
        tpsControlManager.registerTpsPoint(pointName);
        Assert.assertTrue(tpsControlManager.getPoints().containsKey(pointName));
        
    }
    
    /**
     * test denied by monitor key rules.
     */
    @Test
    public void testPatternDeny() {
    
    }
}
