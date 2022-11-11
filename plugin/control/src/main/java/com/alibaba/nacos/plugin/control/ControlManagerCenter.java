package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.ruleactivator.DefaultRuleParser;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParser;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

import java.util.Collection;

public class ControlManagerCenter {
    
    static ControlManagerCenter INSTANCE = null;
    
    private TpsControlManager tpsControlManager;
    
    private ConnectionControlManager connectionControlManager;
    
    private RuleParser ruleParser;
    
    private RuleStorageProxy ruleStorageProxy;
    
    private void initRuleParser() {
        Collection<RuleParser> ruleParsers = NacosServiceLoader.load(RuleParser.class);
        String ruleParserName = ControlConfigs.getInstance().getRuleParser();
        
        for (RuleParser ruleParserInternal : ruleParsers) {
            if (ruleParser.getName().equalsIgnoreCase(ruleParserName)) {
                Loggers.CONTROL.info("Found  rule parser of name={},class={}", ruleParserName,
                        ruleParser.getClass().getSimpleName());
                ruleParser = ruleParserInternal;
                break;
            }
        }
        if (ruleParser == null) {
            Loggers.CONTROL.warn("Fail to rule parser of name ：" + ruleParserName);
            ruleParser = new DefaultRuleParser();
        }
    }
    
    private ControlManagerCenter() {
        tpsControlManager = new TpsControlManager();
        connectionControlManager = new ConnectionControlManager();
        initRuleParser();
        ruleStorageProxy = new RuleStorageProxy();
    }
    
    public RuleStorageProxy getRuleStorageProxy() {
        return ruleStorageProxy;
    }
    
    public RuleParser getRuleParser() {
        return ruleParser;
    }
    
    public TpsControlManager getTpsControlManager() {
        return tpsControlManager;
    }
    
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManager;
    }
    
    public static final ControlManagerCenter getInstance() {
        if (INSTANCE == null) {
            synchronized (ControlManagerCenter.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ControlManagerCenter();
                }
            }
        }
        return INSTANCE;
    }
}
