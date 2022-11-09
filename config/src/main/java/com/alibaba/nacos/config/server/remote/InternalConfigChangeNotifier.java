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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.config.server.service.ConfigRulePersistRuleStorage.DATA_ID_CONNECTION_LIMIT_RULE;
import static com.alibaba.nacos.config.server.service.ConfigRulePersistRuleStorage.DATA_ID_TPS_CONTROL_RULE;
import static com.alibaba.nacos.config.server.service.ConfigRulePersistRuleStorage.NACOS_GROUP;
import static com.alibaba.nacos.config.server.service.ConfigRulePersistRuleStorage.RULE_CONFIG_NAMESPACE;

/**
 * ConfigChangeNotifier.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeNotifier.java, v 0.1 2020年07月20日 3:00 PM liuzunfei Exp $
 */
@Component(value = "internalConfigChangeNotifier")
public class InternalConfigChangeNotifier extends Subscriber<LocalDataChangeEvent> {
    
    public InternalConfigChangeNotifier() {
        NotifyCenter.registerToPublisher(ConnectionLimitRuleChangeEvent.class, 16384);
        NotifyCenter.registerToPublisher(TpsControlRuleChangeEvent.class, 16384);
        NotifyCenter.registerSubscriber(this);
        
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String groupKey = event.groupKey;
        String[] groupKeyElements = GroupKey.parseKey(groupKey);
        String dataId = groupKeyElements[0];
        String group = groupKeyElements[1];
        String tenant = null;
        if (groupKeyElements.length > 2) {
            tenant = groupKeyElements[2];
        }
        if (DATA_ID_CONNECTION_LIMIT_RULE.equals(dataId) && NACOS_GROUP.equals(group) && StringUtils
                .equals(tenant, RULE_CONFIG_NAMESPACE)) {
            
            try {
                NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(true));
                
            } catch (Exception e) {
                Loggers.REMOTE.error("connection limit rule load fail.", e);
            }
        }
        
        if (dataId.startsWith(DATA_ID_TPS_CONTROL_RULE) && NACOS_GROUP.equals(group) && StringUtils
                .equals(tenant, RULE_CONFIG_NAMESPACE)) {
            try {
                String pointName = dataId.replaceFirst(DATA_ID_TPS_CONTROL_RULE, "");
                NotifyCenter.publishEvent(new TpsControlRuleChangeEvent(pointName, true));
                
            } catch (Exception e) {
                Loggers.REMOTE.error("connection limit rule load fail.", e);
            }
            
        }
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
    
}

