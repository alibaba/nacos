package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * control rule activator.
 */
@Component
public class ControlRuleChangeActivator {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    TpsRuleChangeSubscriber tpsRuleChangeSubscriber = new TpsRuleChangeSubscriber();
    
    ConnectionRuleChangeSubscriber connectionRuleChangeSubscriber = new ConnectionRuleChangeSubscriber();
    
    public ControlRuleChangeActivator() {
        NotifyCenter.registerSubscriber(tpsRuleChangeSubscriber);
        NotifyCenter.registerSubscriber(connectionRuleChangeSubscriber);
    }
    
    class TpsRuleChangeSubscriber extends Subscriber<TpsControlRuleChangeEvent> {
        
        @Override
        public void onEvent(TpsControlRuleChangeEvent event) {
            String pointName = event.getPointName();
            LOGGER.info("Tps control rule change event receive,pointName={}, external={} ", pointName,
                    event.isExternal());
            if (event == null || event.getPointName() == null) {
                return;
            }
            try {
                RuleStorageProxy ruleStorageProxy = ControlManagerCenter.getInstance().getRuleStorageProxy();
                
                if (event.isExternal()) {
                    String persistTpsRule = ruleStorageProxy.getExternalDiskStorage().getTpsRule(pointName);
                    ruleStorageProxy.getLocalDiskStorage().saveTpsRule(pointName, persistTpsRule);
                }
                String tpsRuleContent = ruleStorageProxy.getLocalDiskStorage().getTpsRule(pointName);
                
                TpsControlRule tpsControlRule = StringUtils.isBlank(tpsRuleContent) ? new TpsControlRule()
                        : ControlManagerCenter.getInstance().getRuleParser().parseTpsRule(tpsRuleContent);
                
                ControlManagerCenter.getInstance().getTpsControlManager().applyTpsRule(pointName, tpsControlRule);
                
            } catch (Exception e) {
                LOGGER.warn("Tps control rule apply error ,error= ", e);
            }
            
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return TpsControlRuleChangeEvent.class;
        }
    }
    
    class ConnectionRuleChangeSubscriber extends Subscriber<ConnectionLimitRuleChangeEvent> {
        
        @Override
        public void onEvent(ConnectionLimitRuleChangeEvent event) {
            LOGGER.info("connection limit rule change event receive ,external:{}", event.isExternal());
            
            try {
                
                RuleStorageProxy ruleStorageProxy = ControlManagerCenter.getInstance().getRuleStorageProxy();
                
                if (event.isExternal()) {
                    String connectionRule = ruleStorageProxy.getExternalDiskStorage().getConnectionRule();
                    ruleStorageProxy.getLocalDiskStorage().saveConnectionRule(connectionRule);
                }
                String limitRule = ruleStorageProxy.getLocalDiskStorage().getConnectionRule();
                
                Loggers.CONTROL.info("start to apply connection rule content " + limitRule);
                
                ConnectionLimitRule connectionLimitRule = StringUtils.isBlank(limitRule) ? new ConnectionLimitRule()
                        : ControlManagerCenter.getInstance().getRuleParser().parseConnectionRule(limitRule);
                Loggers.CONTROL.info("end to  apply connection rule content ");
                
                if (connectionLimitRule != null) {
                    ControlManagerCenter.getInstance().getConnectionControlManager()
                            .setConnectionLimitRule(connectionLimitRule);
                } else {
                    LOGGER.info("Parse rule is null,Ignore illegal rule  :{}", limitRule);
                }
                
            } catch (Exception e) {
                LOGGER.error("Fail to parse connection limit rule ,persit:{}", event.isExternal(), e);
            }
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return ConnectionLimitRuleChangeEvent.class;
        }
    }
    
}
