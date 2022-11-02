package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlManagerFactory;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * control rule activator.
 */
@Component
public class ControlRuleChangeSubscriber {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlRuleChangeSubscriber.class);
    
    TpsRuleChangeSubscriber tpsRuleChangeSubscriber = new TpsRuleChangeSubscriber();
    
    ConnectionRuleChangeSubscriber connectionRuleChangeSubscriber = new ConnectionRuleChangeSubscriber();
    
    public ControlRuleChangeSubscriber() {
        NotifyCenter.registerSubscriber(tpsRuleChangeSubscriber);
        NotifyCenter.registerSubscriber(connectionRuleChangeSubscriber);
    }
    
    class TpsRuleChangeSubscriber extends Subscriber<TpsControlRuleChangeEvent> {
        
        @Override
        public void onEvent(TpsControlRuleChangeEvent event) {
            String pointName = event.getPointName();
            LOGGER.info("Tps control rule change event receive,pointName={}, persist={} ", pointName,
                    event.isPersist());
            if (event == null || event.getPointName() == null) {
                return;
            }
            try {
                if (event.isPersist()) {
                    String persistTpsRule = PersistRuleActivatorProxy.getInstance().getTpsRule(pointName);
                    LocalDiskRuleActivator.INSTANCE.saveTpsRule(pointName, persistTpsRule);
                }
                String tpsRuleContent = LocalDiskRuleActivator.INSTANCE.getTpsRule(pointName);
                
                TpsControlRule tpsControlRule = StringUtils.isBlank(tpsRuleContent) ? new TpsControlRule()
                        : RuleParserProxy.getInstance().parseTpsRule(tpsRuleContent);
                
                ControlManagerFactory.getInstance().getTpsControlManager().applyTpsRule(pointName, tpsControlRule);
                
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
            LOGGER.info("connection limit rule change event receive ,persit:{}", event.isPersist());
            
            try {
                
                if (event.isPersist()) {
                    String connectionRule = PersistRuleActivatorProxy.getInstance().getConnectionRule();
                    LocalDiskRuleActivator.INSTANCE.saveConnectionRule(connectionRule);
                }
                String limitRule = LocalDiskRuleActivator.INSTANCE.getConnectionRule();
                ConnectionLimitRule connectionLimitRule = StringUtils.isBlank(limitRule) ? new ConnectionLimitRule()
                        : RuleParserProxy.getInstance().parseConnectionRule(limitRule);
                if (connectionLimitRule != null) {
                    ControlManagerFactory.getInstance().getConnectionControlManager()
                            .setConnectionLimitRule(connectionLimitRule);
                } else {
                    LOGGER.info("Parse rule is null,Ignore illegal rule  :{}", limitRule);
                }
                
            } catch (Exception e) {
                LOGGER.error("Fail to parse connection limit rule ,persit:{}", event.isPersist(), e);
            }
        }
        
        
        @Override
        public Class<? extends Event> subscribeType() {
            return ConnectionLimitRuleChangeEvent.class;
        }
    }
    
}
