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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos AI module mcp server change notifier.
 *
 * @author xiweng.yy
 */
public class McpServerChangeNotifier extends Subscriber<McpServerChangedEvent> {
    
    private final Map<String, Set<McpServerListenerInvoker>> mcpServerListenerInvokers;
    
    public McpServerChangeNotifier() {
        this.mcpServerListenerInvokers = new ConcurrentHashMap<>(2);
        
    }
    
    @Override
    public void onEvent(McpServerChangedEvent event) {
        if (!isSubscribed(event.getMcpName())) {
            return;
        }
        NacosMcpServerEvent notifiedEvent = new NacosMcpServerEvent(event.getMcpServer());
        for (McpServerListenerInvoker each : mcpServerListenerInvokers.get(event.getMcpName())) {
            each.invoke(notifiedEvent);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return McpServerChangedEvent.class;
    }
    
    /**
     * register listener.
     *
     * @param mcpName           name of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void registerListener(String mcpName, McpServerListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        mcpServerListenerInvokers.compute(mcpName, (key, mcpServerListenerInvokers) -> {
            if (null == mcpServerListenerInvokers) {
                mcpServerListenerInvokers = new ConcurrentHashSet<>();
            }
            mcpServerListenerInvokers.add(listenerInvoker);
            return mcpServerListenerInvokers;
        });
    }
    
    /**
     * deregister listener.
     *
     * @param mcpName           name of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void deregisterListener(String mcpName, McpServerListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        mcpServerListenerInvokers.compute(mcpName, (key, mcpServerListenerInvokers) -> {
            if (null == mcpServerListenerInvokers) {
                return null;
            }
            mcpServerListenerInvokers.remove(listenerInvoker);
            return mcpServerListenerInvokers.isEmpty() ? null : mcpServerListenerInvokers;
        });
    }
    
    /**
     * check serviceName,groupName is subscribed.
     *
     * @param mcpName           name of mcp server
     * @return is mcp server subscribed
     */
    public boolean isSubscribed(String mcpName) {
        return CollectionUtils.isNotEmpty(mcpServerListenerInvokers.get(mcpName));
    }
}
