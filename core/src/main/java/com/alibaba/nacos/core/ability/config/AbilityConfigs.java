/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.ability.config;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.impl.ServerAbilities;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**.
 * @author Daydreamer
 * @description  Dynamically load ability from config
 * @date 2022/8/31 12:27
 **/
@Configuration
public class AbilityConfigs extends Subscriber<ServerConfigChangeEvent> {
    
    public static final String PREFIX = "nacos.core.ability.";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbilityConfigs.class);
    
    private final Set<AbilityKey> serverAbilityKeys = new ConcurrentHashSet<>();
    
    private AbstractAbilityControlManager abilityHandlerRegistry = NacosAbilityManagerHolder.getInstance();
    
    public AbilityConfigs() {
        // load ability
        serverAbilityKeys.addAll(ServerAbilities.getStaticAbilities().keySet());
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        // load config
        Map<AbilityKey, Boolean> newValues = new HashMap<>(serverAbilityKeys.size());
        serverAbilityKeys.forEach(abilityKey -> {
            String key = PREFIX + abilityKey.getName();
            try {
                // scan
                Boolean property = EnvUtil.getProperty(key, Boolean.class);
                if (property != null) {
                    newValues.put(abilityKey, property);
                }
            } catch (Exception e) {
                LOGGER.warn("Update ability config from env failed, use old val, ability : {} , because : {}", key, e);
            }
        });
        // update
        refresh(newValues);
    }
    
    /**.
     * refresh ability
     */
    private void refresh(Map<AbilityKey, Boolean> newValues) {
        newValues.forEach((abilityKey, val) -> {
            // do nothing if has turned on/off
            if (val) {
                abilityHandlerRegistry.enableCurrentNodeAbility(abilityKey);
            } else {
                abilityHandlerRegistry.disableCurrentNodeAbility(abilityKey);
            }
        });
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
    
    @JustForTest
    protected Set<AbilityKey> getServerAbilityKeys() {
        return serverAbilityKeys;
    }
    
    @JustForTest
    protected void setAbilityHandlerRegistry(AbstractAbilityControlManager abilityHandlerRegistry) {
        this.abilityHandlerRegistry = abilityHandlerRegistry;
    }
    
}
