package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.plugin.control.ControlManagerFactory;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ControlRuleAutoWatcher {
    
    private static ScheduledExecutorService executorService;
    
    public ControlRuleAutoWatcher() {
        if (executorService == null) {
            executorService = ExecutorFactory.newSingleScheduledExecutorService(
                    new NameThreadFactory(ControlRuleAutoWatcher.class.getSimpleName()));
            executorService.scheduleWithFixedDelay(this::checkChange, TimeUnit.SECONDS.toMillis(5),
                    TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS);
        }
    }
    
    private void checkChange() {
        if (ControlManagerFactory.getInstance().getTpsControlManager() != null) {
            Map<String, TpsBarrier> points = ControlManagerFactory.getInstance().getTpsControlManager().getPoints();
            for (String pointName : points.keySet()) {
                //TODO
            }
        }
        
    }
    
    Map<String, String> lastTpsRuleContext = new HashMap<>();
    
    String connectionRuleContent;
}
