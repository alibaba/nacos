/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Bridges server static configuration changes to unified plugin configuration refresh.
 *
 * @author Nacos
 */
@Component
public class PluginConfigRefreshSubscriber extends Subscriber<ServerConfigChangeEvent>
    implements InitializingBean, DisposableBean {
    
    private static final Executor EXECUTOR = GlobalExecutor::executeByCommon;
    
    private final PluginManager pluginManager;
    
    public PluginConfigRefreshSubscriber(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
    
    @Override
    public void afterPropertiesSet() {
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void destroy() {
        NotifyCenter.deregisterSubscriber(this);
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        pluginManager.refreshPluginTypePolicies();
        pluginManager.refreshStaticPluginConfigs();
    }
    
    @Override
    public Executor executor() {
        return EXECUTOR;
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
