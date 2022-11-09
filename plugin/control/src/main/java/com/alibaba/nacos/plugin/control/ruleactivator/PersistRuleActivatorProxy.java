package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import org.slf4j.Logger;

import java.util.Collection;

public class PersistRuleActivatorProxy {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private static PersistRuleStorage instance = null;
    
    static {
        Collection<PersistRuleStorage> persistRuleActivators = NacosServiceLoader.load(PersistRuleStorage.class);
        String rulePersistActivator = ControlConfigs.getInstance().getRulePersistActivator();
        
        for (PersistRuleStorage persistRuleActivator : persistRuleActivators) {
            if (persistRuleActivator.getName().equalsIgnoreCase(rulePersistActivator)) {
                LOGGER.info("Found persist rule activator of name ：" + rulePersistActivator);
                instance = persistRuleActivator;
                break;
            }
        }
        if (instance == null) {
            LOGGER.error("Fail to found persist rule activator of name ：" + rulePersistActivator);
            
        }
    }
    
    public static PersistRuleStorage getInstance() {
        return instance;
    }
    
}
