/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.control.ruleactivator;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.slf4j.Logger;

/**
 * control rule activator.
 *
 * @author shiyiyue
 */
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
                    if (ruleStorageProxy.getExternalStorage() != null) {
                        String persistTpsRule = ruleStorageProxy.getExternalStorage().getTpsRule(pointName);
                        ruleStorageProxy.getLocalDiskStorage().saveTpsRule(pointName, persistTpsRule);
                    } else {
                        Loggers.CONTROL
                                .info("No external rule storage found,will load local disk instead,point name={}",
                                        event.getPointName());
                    }
                    
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
                    if (ruleStorageProxy.getExternalStorage() != null) {
                        String connectionRule = ruleStorageProxy.getExternalStorage().getConnectionRule();
                        ruleStorageProxy.getLocalDiskStorage().saveConnectionRule(connectionRule);
                    } else {
                        Loggers.CONTROL.info("No external rule storage found,will load local disk instead");
                        
                    }
                    
                }
                String limitRule = ruleStorageProxy.getLocalDiskStorage().getConnectionRule();
                
                Loggers.CONTROL.info("start to apply connection rule content " + limitRule);
                
                ConnectionControlRule connectionControlRule =
                        StringUtils.isBlank(limitRule) ? new ConnectionControlRule()
                                : ControlManagerCenter.getInstance().getRuleParser().parseConnectionRule(limitRule);
                Loggers.CONTROL.info("end to  apply connection rule content ");
                
                if (connectionControlRule != null) {
                    ControlManagerCenter.getInstance().getConnectionControlManager()
                            .applyConnectionLimitRule(connectionControlRule);
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
