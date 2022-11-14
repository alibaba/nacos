package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.ruleactivator.NacosRuleParser;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleParser;
import com.alibaba.nacos.plugin.control.ruleactivator.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

import java.util.Collection;

public class ControlManagerCenter {
    
    static ControlManagerCenter instance = null;
    
    private TpsControlManager tpsControlManager;
    
    private ConnectionControlManager connectionControlManager;
    
    private RuleParser ruleParser;
    
    private RuleStorageProxy ruleStorageProxy;
    
    private void initRuleParser() {
        Collection<RuleParser> ruleParsers = NacosServiceLoader.load(RuleParser.class);
        String ruleParserName = ControlConfigs.getInstance().getRuleParser();
        
        for (RuleParser ruleParserInternal : ruleParsers) {
            if (ruleParserInternal.getName().equalsIgnoreCase(ruleParserName)) {
                Loggers.CONTROL.info("Found  rule parser of name={},class={}", ruleParserName,
                        ruleParserInternal.getClass().getSimpleName());
                ruleParser = ruleParserInternal;
                break;
            }
        }
        if (ruleParser == null) {
            Loggers.CONTROL.warn("Fail to rule parser of name ：" + ruleParserName);
            ruleParser = new NacosRuleParser();
        }
        
        Collection<ConnectionControlManager> connectionControlManagers = NacosServiceLoader
                .load(ConnectionControlManager.class);
        String connectionManagerName = ControlConfigs.getInstance().getConnectionManager();
        
        for (ConnectionControlManager connectionControlManagerInternal : connectionControlManagers) {
            if (connectionControlManagerInternal.getName().equalsIgnoreCase(connectionManagerName)) {
                Loggers.CONTROL.info("Found  rule parser of name={},class={}", ruleParserName,
                        connectionControlManagerInternal.getClass().getSimpleName());
                connectionControlManager = connectionControlManagerInternal;
                break;
            }
        }
        if (connectionControlManager == null) {
            Loggers.CONTROL.warn("Fail to rule parser of name ：" + ruleParserName);
            connectionControlManager = new NacosConnectionControlManager();
        }
        
    }
   
    private void initConnectionManager() {
        
        Collection<ConnectionControlManager> connectionControlManagers = NacosServiceLoader
                .load(ConnectionControlManager.class);
        String connectionManagerName = ControlConfigs.getInstance().getConnectionManager();
        
        for (ConnectionControlManager connectionControlManagerInternal : connectionControlManagers) {
            if (connectionControlManagerInternal.getName().equalsIgnoreCase(connectionManagerName)) {
                Loggers.CONTROL.info("Found  connection control manager of name={},class={}", connectionManagerName,
                        connectionControlManagerInternal.getClass().getSimpleName());
                connectionControlManager = connectionControlManagerInternal;
                break;
            }
        }
        if (connectionControlManager == null) {
            Loggers.CONTROL.warn("Fail to connection control manager of name ：" + connectionManagerName);
            connectionControlManager = new NacosConnectionControlManager();
        }
        
    }
    
    private ControlManagerCenter() {
        tpsControlManager = new TpsControlManager();
        initRuleParser();
        initConnectionManager();
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
        if (instance == null) {
            synchronized (ControlManagerCenter.class) {
                if (instance == null) {
                    instance = new ControlManagerCenter();
                }
            }
        }
        return instance;
    }
    
    public void reloadTpsControlRule(String pointName, boolean external) {
        NotifyCenter.publishEvent(new TpsControlRuleChangeEvent(pointName, external));
    }
    
    public void reloadConnectionControlRule(boolean external) {
        NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(external));
    }
    
}
