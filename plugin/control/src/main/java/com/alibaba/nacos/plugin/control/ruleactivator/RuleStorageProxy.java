package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import org.slf4j.Logger;

import java.util.Collection;

public class RuleStorageProxy {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private static final RuleStorageProxy INSTANCE = new RuleStorageProxy();
    
    private LocalDiskRuleStorage localDiskRuleStorage = new LocalDiskRuleStorage();
    
    private ExternalRuleStorage externalRuleStorage = null;
    
    public RuleStorageProxy() {
        Collection<ExternalRuleStorage> persistRuleActivators = NacosServiceLoader.load(ExternalRuleStorage.class);
        String rulePersistActivator = ControlConfigs.getInstance().getRulePersistActivator();
        
        for (ExternalRuleStorage persistRuleActivator : persistRuleActivators) {
            if (persistRuleActivator.getName().equalsIgnoreCase(rulePersistActivator)) {
                LOGGER.info("Found persist rule storage of name ：" + rulePersistActivator);
                externalRuleStorage = persistRuleActivator;
                break;
            }
        }
        if (externalRuleStorage == null) {
            LOGGER.error("Fail to found persist rule storage of name ：" + rulePersistActivator);
            
        }
    }
    
    public RuleStorage getLocalDiskStorage() {
        return localDiskRuleStorage;
    }
    
    public RuleStorage getExternalDiskStorage() {
        return externalRuleStorage;
    }
    
    public static final RuleStorageProxy getInstance() {
        return INSTANCE;
    }
}
