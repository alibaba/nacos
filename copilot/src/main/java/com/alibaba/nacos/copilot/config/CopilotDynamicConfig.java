/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refreshes Copilot configuration after the server configuration file changes.
 *
 * <p>Modeled on core's {@code AbstractDynamicConfig}, implemented here because copilot must
 * not depend on nacos-core.
 *
 * TODO: merge with core's AbstractDynamicConfig in the future. See #15906.
 *
 * <p>Only server-side deployments ({@code merged} or {@code server}) watch the configuration
 * file, so a console deployment still needs a restart. Removed keys are also not visible to a
 * running process, which is a limitation of the underlying watcher rather than of this
 * subscriber.
 *
 * @author nacos
 */
@Component
public class CopilotDynamicConfig extends Subscriber<ServerConfigChangeEvent>
    implements InitializingBean, DisposableBean {
    
    private final CopilotAgentManager agentManager;
    
    // Refreshing re-reads storage and probes connections, so it must not run on the shared
    // event publisher thread that also serves the other ServerConfigChangeEvent subscribers.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().daemon(true)
            .nameFormat("nacos-copilot-config-refresh-%d").build());
    
    public CopilotDynamicConfig(CopilotAgentManager agentManager) {
        this.agentManager = agentManager;
    }
    
    @Override
    public void afterPropertiesSet() {
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void destroy() {
        NotifyCenter.deregisterSubscriber(this);
        executor.shutdownNow();
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        agentManager.refreshConfig();
    }
    
    @Override
    public Executor executor() {
        return executor;
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
