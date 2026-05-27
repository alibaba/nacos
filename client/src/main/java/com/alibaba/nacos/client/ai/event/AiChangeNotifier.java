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
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
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
    
    private final Map<String, Set<PromptListenerInvoker>> promptListenerInvokers;
    
    private final Map<String, Set<AgentSpecListenerInvoker>> agentSpecListenerInvokers;
    
    private final Map<String, Set<SkillListenerInvoker>> skillListenerInvokers;
    
    public AiChangeNotifier() {
        this.mcpServerListenerInvokers = new ConcurrentHashMap<>(2);
        this.agentCardListenerInvokers = new ConcurrentHashMap<>(2);
        this.promptListenerInvokers = new ConcurrentHashMap<>(2);
        this.agentSpecListenerInvokers = new ConcurrentHashMap<>(2);
        this.skillListenerInvokers = new ConcurrentHashMap<>(2);
    }
    
    @Override
    public void onEvent(Event event) {
        if (event instanceof McpServerChangedEvent) {
            handleMcpServerChangedEvent((McpServerChangedEvent) event);
        } else if (event instanceof AgentCardChangedEvent) {
            handleAgentCardChangedEvent((AgentCardChangedEvent) event);
        } else if (event instanceof PromptChangedEvent) {
            handlePromptChangedEvent((PromptChangedEvent) event);
        } else if (event instanceof AgentSpecChangedEvent) {
            handleAgentSpecChangedEvent((AgentSpecChangedEvent) event);
        } else if (event instanceof SkillChangedEvent) {
            handleSkillChangedEvent((SkillChangedEvent) event);
        }
    }
    
    private void handleMcpServerChangedEvent(McpServerChangedEvent event) {
        String mcpServerKey =
            CacheKeyUtils.buildMcpServerKey(event.getMcpName(), event.getVersion());
        if (!isSubscribed(mcpServerKey, mcpServerListenerInvokers)) {
            return;
        }
        NacosMcpServerEvent notifiedEvent = new NacosMcpServerEvent(event.getMcpServer());
        for (McpServerListenerInvoker each : mcpServerListenerInvokers.get(mcpServerKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    private void handleAgentCardChangedEvent(AgentCardChangedEvent event) {
        String agentCardKey =
            CacheKeyUtils.buildAgentCardKey(event.getAgentName(), event.getVersion());
        if (!isSubscribed(agentCardKey, agentCardListenerInvokers)) {
            return;
        }
        NacosAgentCardEvent notifiedEvent = new NacosAgentCardEvent(event.getAgentCard());
        for (AgentCardListenerInvoker each : agentCardListenerInvokers.get(agentCardKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    private void handlePromptChangedEvent(PromptChangedEvent event) {
        String promptCacheKey = event.getCacheKey();
        if (!isSubscribed(promptCacheKey, promptListenerInvokers)) {
            return;
        }
        NacosPromptEvent notifiedEvent =
            new NacosPromptEvent(event.getPromptKey(), event.getPrompt());
        for (PromptListenerInvoker each : promptListenerInvokers.get(promptCacheKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    private void handleAgentSpecChangedEvent(AgentSpecChangedEvent event) {
        String agentSpecKey = CacheKeyUtils.buildAgentSpecKey(event.getAgentSpecName());
        if (!isSubscribed(agentSpecKey, agentSpecListenerInvokers)) {
            return;
        }
        NacosAgentSpecEvent notifiedEvent =
            new NacosAgentSpecEvent(event.getAgentSpecName(), event.getAgentSpec());
        for (AgentSpecListenerInvoker each : agentSpecListenerInvokers.get(agentSpecKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    private void handleSkillChangedEvent(SkillChangedEvent event) {
        String skillCacheKey = event.getCacheKey();
        if (!isSubscribed(skillCacheKey, skillListenerInvokers)) {
            return;
        }
        NacosSkillEvent notifiedEvent = new NacosSkillEvent(event.getSkillName(),
            event.getZipBytes(), event.getMd5(), event.getResolvedVersion());
        for (SkillListenerInvoker each : skillListenerInvokers.get(skillCacheKey)) {
            each.invoke(notifiedEvent);
        }
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> listenedEventTypes = new LinkedList<>();
        listenedEventTypes.add(McpServerChangedEvent.class);
        listenedEventTypes.add(AgentCardChangedEvent.class);
        listenedEventTypes.add(PromptChangedEvent.class);
        listenedEventTypes.add(AgentSpecChangedEvent.class);
        listenedEventTypes.add(SkillChangedEvent.class);
        return listenedEventTypes;
    }
    
    /**
     * register mcp server listener.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void registerListener(String mcpName, String version,
        McpServerListenerInvoker listenerInvoker) {
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
    public void registerListener(String agentName, String version,
        AgentCardListenerInvoker listenerInvoker) {
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
     * register prompt listener.
     *
     * @param promptKey       prompt key
     * @param listenerInvoker listener invoker
     */
    public void registerListener(String promptKey, String version, String label,
        PromptListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String key = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        promptListenerInvokers.compute(key, (k, promptListenerInvokers) -> {
            if (null == promptListenerInvokers) {
                promptListenerInvokers = new ConcurrentHashSet<>();
            }
            promptListenerInvokers.add(listenerInvoker);
            return promptListenerInvokers;
        });
    }
    
    /**
     * register agent spec listener.
     *
     * @param agentSpecName   name of agent spec
     * @param listenerInvoker listener invoker
     */
    public void registerListener(String agentSpecName, AgentSpecListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String agentSpecKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        agentSpecListenerInvokers.compute(agentSpecKey, (key, agentSpecListenerInvokers) -> {
            if (null == agentSpecListenerInvokers) {
                agentSpecListenerInvokers = new ConcurrentHashSet<>();
            }
            agentSpecListenerInvokers.add(listenerInvoker);
            return agentSpecListenerInvokers;
        });
    }
    
    /**
     * register skill listener.
     *
     * @param skillName       name of skill
     * @param version         version of skill
     * @param label           label of skill
     * @param listenerInvoker listener invoker
     */
    public void registerListener(String skillName, String version, String label,
        SkillListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String key = CacheKeyUtils.buildSkillKey(skillName, version, label);
        skillListenerInvokers.compute(key, (k, skillListenerInvokers) -> {
            if (null == skillListenerInvokers) {
                skillListenerInvokers = new ConcurrentHashSet<>();
            }
            skillListenerInvokers.add(listenerInvoker);
            return skillListenerInvokers;
        });
    }
    
    /**
     * deregister mcp server listener.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param listenerInvoker   listener invoker
     */
    public void deregisterListener(String mcpName, String version,
        McpServerListenerInvoker listenerInvoker) {
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
    public void deregisterListener(String agentName, String version,
        AgentCardListenerInvoker listenerInvoker) {
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
     * deregister prompt listener.
     *
     * @param promptKey       prompt key
     * @param listenerInvoker listener invoker
     */
    public void deregisterListener(String promptKey, String version, String label,
        PromptListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String key = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        promptListenerInvokers.compute(key, (k, promptListenerInvokers) -> {
            if (null == promptListenerInvokers) {
                return null;
            }
            promptListenerInvokers.remove(listenerInvoker);
            return promptListenerInvokers.isEmpty() ? null : promptListenerInvokers;
        });
    }
    
    /**
     * deregister agent spec listener.
     *
     * @param agentSpecName   name of agent spec
     * @param listenerInvoker listener invoker
     */
    public void deregisterListener(String agentSpecName, AgentSpecListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String agentSpecKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        agentSpecListenerInvokers.compute(agentSpecKey, (key, agentSpecListenerInvokers) -> {
            if (null == agentSpecListenerInvokers) {
                return null;
            }
            agentSpecListenerInvokers.remove(listenerInvoker);
            return agentSpecListenerInvokers.isEmpty() ? null : agentSpecListenerInvokers;
        });
    }
    
    /**
     * deregister skill listener.
     *
     * @param skillName       name of skill
     * @param version         version of skill
     * @param label           label of skill
     * @param listenerInvoker listener invoker
     */
    public void deregisterListener(String skillName, String version, String label,
        SkillListenerInvoker listenerInvoker) {
        if (listenerInvoker == null) {
            return;
        }
        String key = CacheKeyUtils.buildSkillKey(skillName, version, label);
        skillListenerInvokers.compute(key, (k, skillListenerInvokers) -> {
            if (null == skillListenerInvokers) {
                return null;
            }
            skillListenerInvokers.remove(listenerInvoker);
            return skillListenerInvokers.isEmpty() ? null : skillListenerInvokers;
        });
    }
    
    /**
     * check agent spec is subscribed.
     *
     * @param agentSpecName name of agent spec
     * @return is agent spec subscribed
     */
    public boolean isAgentSpecSubscribed(String agentSpecName) {
        String agentSpecKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        return isSubscribed(agentSpecKey, agentSpecListenerInvokers);
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
    
    /**
     * check prompt is subscribed.
     *
     * @param promptKey prompt key
     * @return is prompt subscribed
     */
    public boolean isPromptSubscribed(String promptKey, String version, String label) {
        String key = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        return isSubscribed(key, promptListenerInvokers);
    }
    
    /**
     * check skill is subscribed.
     *
     * @param skillName name of skill
     * @param version   version of skill
     * @param label     label of skill
     * @return is skill subscribed
     */
    public boolean isSkillSubscribed(String skillName, String version, String label) {
        String key = CacheKeyUtils.buildSkillKey(skillName, version, label);
        return isSubscribed(key, skillListenerInvokers);
    }
    
    private <T extends AbstractAiListenerInvoker<?, ?>> boolean isSubscribed(String key,
        Map<String, Set<T>> listenerInvokers) {
        return CollectionUtils.isNotEmpty(listenerInvokers.get(key));
    }
}
