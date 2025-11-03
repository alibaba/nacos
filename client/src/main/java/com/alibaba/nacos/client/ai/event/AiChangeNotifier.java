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

import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos AI module mcp server change notifier.
 *
 * @author xiweng.yy
 */
public class AiChangeNotifier extends SmartSubscriber {
    
    private final Map<String, Set<McpServerListenerInvoker>> mcpServerListenerInvokers;
    
    private final Map<String, Set<AgentCardListenerInvoker>> agentCardListenerInvokers;
    
    public AiChangeNotifier() {
        this.mcpServerListenerInvokers = new ConcurrentHashMap<>(2);
        this.agentCardListenerInvokers = new ConcurrentHashMap<>(2);
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof McpServerChangedEvent) {
            handleMcpServerChangedEvent((McpServerChangedEvent) event);
        } else if (event instanceof AgentCardChangedEvent) {
            handleAgentCardChangedEvent((AgentCardChangedEvent) event);
        }
    }
    
    private void handleMcpServerChangedEvent(McpServerChangedEvent event) {
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(event.getMcpName(), event.getVersion());
        if (!isSubscribed(mcpServerKey, mcpServerListenerInvokers)) {
            return;
        }
        NacosMcpServerEvent notifiedEvent = new NacosMcpServerEvent(event.getMcpServer());
        for (McpServerListenerInvoker each : mcpServerListenerInvokers.get(mcpServerKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    private void handleAgentCardChangedEvent(AgentCardChangedEvent event) {
        String agentCardKey = CacheKeyUtils.buildAgentCardKey(event.getAgentName(), event.getVersion());
        if (!isSubscribed(agentCardKey, agentCardListenerInvokers)) {
            return;
        }
        NacosAgentCardEvent notifiedEvent = new NacosAgentCardEvent(event.getAgentCard());
        for (AgentCardListenerInvoker each : agentCardListenerInvokers.get(agentCardKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> listenedEventTypes = new LinkedList<>();
        listenedEventTypes.add(McpServerChangedEvent.class);
        listenedEventTypes.add(AgentCardChangedEvent.class);
        return listenedEventTypes;
    }
    
    /**
     * register mcp server listener.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void registerListener(String mcpName, String version, McpServerListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        mcpServerListenerInvokers.compute(mcpServerKey, (key, mcpServerListenerInvokers) -> {
            if (null == mcpServerListenerInvokers) {
                mcpServerListenerInvokers = new ConcurrentHashSet<>();
            }
            mcpServerListenerInvokers.add(listenerInvoker);
            return mcpServerListenerInvokers;
        });
    }
    
    /**
     * register agent card listener.
     *
     * @param agentName         name of agent card
     * @param version           version of agent card
     * @param listenerInvoker   listener invoker
     */
    public void registerListener(String agentName, String version, AgentCardListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String agentCardKey = CacheKeyUtils.buildAgentCardKey(agentName, version);
        agentCardListenerInvokers.compute(agentCardKey, (key, agentCardListenerInvokers) -> {
            if (null == agentCardListenerInvokers) {
                agentCardListenerInvokers = new ConcurrentHashSet<>();
            }
            agentCardListenerInvokers.add(listenerInvoker);
            return agentCardListenerInvokers;
        });
    }
    
    /**
     * deregister mcp server listener.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void deregisterListener(String mcpName, String version, McpServerListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        mcpServerListenerInvokers.compute(mcpServerKey, (key, mcpServerListenerInvokers) -> {
            if (null == mcpServerListenerInvokers) {
                return null;
            }
            mcpServerListenerInvokers.remove(listenerInvoker);
            return mcpServerListenerInvokers.isEmpty() ? null : mcpServerListenerInvokers;
        });
    }
    
    /**
     * deregister agent card listener.
     *
     * @param agentName         name of agent card
     * @param version           version of agent card
     * @param listenerInvoker   listener invoker
     */
    public void deregisterListener(String agentName, String version, AgentCardListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String agentCardKey = CacheKeyUtils.buildAgentCardKey(agentName, version);
        agentCardListenerInvokers.compute(agentCardKey, (key, agentCardListenerInvokers) -> {
            if (null == agentCardListenerInvokers) {
                return null;
            }
            agentCardListenerInvokers.remove(listenerInvoker);
            return agentCardListenerInvokers.isEmpty() ? null : agentCardListenerInvokers;
        });
    }
    
    /**
     * check mcp server is subscribed.
     *
     * @param mcpName name of mcp server
     * @param version version of mcp server
     * @return is mcp server subscribed
     */
    public boolean isMcpServerSubscribed(String mcpName, String version) {
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        return isSubscribed(mcpServerKey, mcpServerListenerInvokers);
    }
    
    /**
     * check agent card is subscribed.
     *
     * @param agentName name of agent card
     * @param version version of agent card
     * @return is agent card subscribed
     */
    public boolean isAgentCardSubscribed(String agentName, String version) {
        String agentCardKey = CacheKeyUtils.buildAgentCardKey(agentName, version);
        return isSubscribed(agentCardKey, agentCardListenerInvokers);
    }
    
    private <T extends AbstractAiListenerInvoker> boolean isSubscribed(String key,
            Map<String, Set<T>> listenerInvokers) {
        return CollectionUtils.isNotEmpty(listenerInvokers.get(key));
    }
}
