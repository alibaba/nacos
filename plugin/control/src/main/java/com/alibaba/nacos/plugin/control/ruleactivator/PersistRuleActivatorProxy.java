package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.tps.RuleBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class PersistRuleActivatorProxy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TpsBarrier.class);
    
    private static PersistRuleActivator INSTANCE = null;
    
    static {
        Collection<PersistRuleActivator> persistRuleActivators = NacosServiceLoader.load(PersistRuleActivator.class);
        String rulePersistActivator = ControlConfigs.getInstance().getRulePersistActivator();
        
        for (PersistRuleActivator persistRuleActivator : persistRuleActivators) {
            if (persistRuleActivator.getName().equalsIgnoreCase(rulePersistActivator)) {
                LOGGER.info("Found persist rule activator of name ：" + rulePersistActivator);
                INSTANCE = persistRuleActivator;
                break;
            }
        }
        if (INSTANCE == null) {
            LOGGER.error("Fail to found persist rule activator of name ：" + rulePersistActivator);
            
        }
    }
    
    public static PersistRuleActivator getInstance() {
        return INSTANCE;
    }
 
}
