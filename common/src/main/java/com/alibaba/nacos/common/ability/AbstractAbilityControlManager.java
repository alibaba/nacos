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

package com.alibaba.nacos.common.ability;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.register.AbstractAbilityRegistry;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**.
 * @author Daydreamer
 * @description It is a capability control center, manager current node abilities or other control.
 * @date 2022/7/12 19:18
 **/
public abstract class AbstractAbilityControlManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAbilityControlManager.class);
    
    /**.
     * These handlers will be invoked when the flag of ability change key:
     * ability key from {@link com.alibaba.nacos.api.ability.constant.AbilityKey} value:
     * components who want to be invoked if its interested ability turn on/off
     */
    private final Map<AbilityKey, List<HandlerWithPriority>> handlerMappings = new ConcurrentHashMap<>();
    
    /**.
     * run for HandlerMapping
     */
    private final Executor simpleThreadPool = ExecutorFactory.newSingleExecutorService();
    
    /**.
     * ability current node running
     */
    protected final Map<AbilityKey, Boolean> currentRunningAbility = new ConcurrentHashMap<>();
    
    private final ReentrantLock lockForHandlerMappings = new ReentrantLock();
    
    protected AbstractAbilityControlManager() {
        ThreadUtils.addShutdownHook(this::destroy);
        NotifyCenter.registerToPublisher(AbilityUpdateEvent.class, 16384);
        currentRunningAbility.putAll(initCurrentNodeAbilities());
    }
    
    /**
     * . Turn on the ability whose key is <p>abilityKey</p>
     *
     * @param abilityKey ability key
     * @return if turn success
     */
    public boolean enableCurrentNodeAbility(AbilityKey abilityKey) {
        return doTurn(true, abilityKey);
    }
    
    /**
     * . Turn off the ability whose key is <p>abilityKey</p>
     *
     * @param abilityKey ability key
     * @return if turn success
     */
    public boolean disableCurrentNodeAbility(AbilityKey abilityKey) {
        return doTurn(false, abilityKey);
    }
    
    public boolean isCurrentNodeAbilityRunning(AbilityKey abilityKey) {
        return currentRunningAbility.getOrDefault(abilityKey, false);
    }
    
    /**.
     * Init current node abilities
     *
     * @return current node abilities
     */
    protected abstract Map<AbilityKey, Boolean> initCurrentNodeAbilities();
    
    /**.
     * Return the abilities current node
     *
     * @return current abilities
     */
    public Map<AbilityKey, Boolean> getCurrentNodeAbilities() {
        return Collections.unmodifiableMap(currentRunningAbility);
    }
    
    /**.
     * Turn on/off the ability of current node
     *
     * @param isOn       is on
     * @param abilityKey ability key from {@link AbilityKey}
     * @return if turn success
     */
    private boolean doTurn(boolean isOn, AbilityKey abilityKey) {
        Boolean isEnabled = currentRunningAbility.get(abilityKey);
        // if not supporting this key
        if (isEnabled == null) {
            LOGGER.warn("[AbilityControlManager] Attempt to turn on/off a not existed ability!");
            return false;
        } else if (isOn == isEnabled) {
            // if already turn on/off
            return true;
        }
        // turn on/off
        currentRunningAbility.put(abilityKey, isOn);
        // handler mappings
        triggerHandlerMappingAsyn(abilityKey, isOn, this.handlerMappings);
        // notify event
        AbilityUpdateEvent abilityUpdateEvent = new AbilityUpdateEvent();
        abilityUpdateEvent.setTable(Collections.unmodifiableMap(currentRunningAbility));
        abilityUpdateEvent.isOn = isOn;
        abilityUpdateEvent.abilityKey = abilityKey;
        NotifyCenter.publishEvent(abilityUpdateEvent);
        return true;
    }
    
    /**
     * Register the component which is managed by {@link AbstractAbilityControlManager}. if you are hoping that a
     * component will be invoked when turn on/off the ability whose key is <p>abilityKey</p>.
     *
     * @param abilityKey     component key.
     * @param priority       the higher the priority is, the faster it will be called.
     * @param handlerMapping component instance.
     */
    public void registerComponent(AbilityKey abilityKey, HandlerMapping handlerMapping, int priority) {
        doRegisterComponent(abilityKey, handlerMapping, this.handlerMappings, lockForHandlerMappings, priority, currentRunningAbility);
    }
    
    /**.
     * Register component with the lowest priority
     *
     * @param abilityKey ability key
     * @param handlerMapping handler
     */
    public void registerComponent(AbilityKey abilityKey, HandlerMapping handlerMapping) {
        registerComponent(abilityKey, handlerMapping, -1);
    }
    
    /**.
     * Remove the specific type handler for a certain ability
     *
     * @param abilityKey ability key
     * @param handlerMappingClazz type
     * @return the count of handlers are removed
     */
    public int removeComponent(AbilityKey abilityKey, Class<? extends HandlerMapping> handlerMappingClazz) {
        return doRemove(abilityKey, handlerMappingClazz, lockForHandlerMappings, handlerMappings);
    }
    
    /**.
     * Close
     */
    public final void destroy() {
        LOGGER.warn("[DefaultAbilityControlManager] - Start destroying...");
        ((ThreadPoolExecutor) simpleThreadPool).shutdown();
        if (MapUtil.isNotEmpty(handlerMappings)) {
            handlerMappings.keySet().forEach(key -> doTriggerSyn(key, false, handlerMappings));
        }
        // hook
        doDestroy();
        LOGGER.warn("[DefaultAbilityControlManager] - Destruction of the end");
    }
    
    /**.
     * Combine with current node abilities, in order to get abilities current node provides
     *
     * @param abilities combined ability table
     */
    public void combine(Map<AbilityKey, Boolean> abilities) {
        currentRunningAbility.forEach((k, v) -> {
            Boolean isCurrentSupport = currentRunningAbility.get(k);
            if (isCurrentSupport != null) {
                abilities.put(k, abilities.getOrDefault(k, false) && isCurrentSupport);
            }
        });
    }
    
    /**.
     * hook for subclass
     */
    protected void doDestroy() {
        // for server ability manager
    }
    
    /**
     * Remove the component instance of <p>handlerMappingClazz</p>.
     *
     * @param abilityKey ability key from {@link AbstractAbilityRegistry}
     * @param handlerMappingClazz implement of {@link HandlerMapping}
     * @param lock lock for operation
     * @param handlerMappingsMap handler collection map
     * @return the count of components have removed
     */
    protected int doRemove(AbilityKey abilityKey, Class<? extends HandlerMapping> handlerMappingClazz, Lock lock,
            Map<AbilityKey, List<HandlerWithPriority>> handlerMappingsMap) {
        List<HandlerWithPriority> handlerMappings = handlerMappingsMap.get(abilityKey);
        if (CollectionUtils.isEmpty(handlerMappings)) {
            return 0;
        }
        lock.lock();
        try {
            AtomicInteger count = new AtomicInteger();
            handlerMappings.removeIf(item -> {
                if (item.handlerMapping.getClass().equals(handlerMappingClazz)) {
                    count.getAndIncrement();
                    return true;
                }
                return false;
            });
            return count.get();
        } finally {
            lock.unlock();
        }
    }
    
    public int removeAll(AbilityKey abilityKey) {
        List<HandlerWithPriority> remove = this.handlerMappings.remove(abilityKey);
        return Optional.ofNullable(remove).orElse(Collections.emptyList()).size();
    }
    
    /**.
     * Register the component into handlerMappings locking by lockForHandlerMappings to ensure concurrency security.
     *
     * @param abilityKey             ability key
     * @param handlerMapping         component instance.
     * @param handlerMappings        container
     * @param lockForHandlerMappings lock to ensure concurrency
     * @param abilityTable           behavioral basis of handler
     */
    protected void doRegisterComponent(AbilityKey abilityKey, HandlerMapping handlerMapping,
            Map<AbilityKey, List<HandlerWithPriority>> handlerMappings, Lock lockForHandlerMappings,
            int priority, Map<AbilityKey, Boolean> abilityTable) {
        if (!currentRunningAbility.containsKey(abilityKey)) {
            LOGGER.warn("[AbilityHandlePostProcessor] Failed to register processor: {}, because illegal key!",
                    handlerMapping.getClass().getSimpleName());
        }
        
        // legal key
        lockForHandlerMappings.lock();
        try {
            List<HandlerWithPriority> handlers = handlerMappings.getOrDefault(abilityKey, new CopyOnWriteArrayList<>());
            HandlerWithPriority handlerWithPriority = new HandlerWithPriority(handlerMapping, priority);
            handlers.add(handlerWithPriority);
            handlerMappings.put(abilityKey, handlers);
            // choose behavior
            // enable default
            if (abilityTable.getOrDefault(abilityKey, false)) {
                handlerMapping.enable();
            } else {
                handlerMapping.disable();
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("[DefaultAbilityControlManager] Fail to register handler: {}", handlerMapping.getClass().getSimpleName());
        } finally {
            lockForHandlerMappings.unlock();
            LOGGER.info("[DefaultAbilityControlManager] Successfully registered processor: {}",
                    handlerMapping.getClass().getSimpleName());
        }
    }
    
    /**
     * Invoke componments which linked to ability key asyn.
     *
     * @param key ability key from {@link AbstractAbilityRegistry}
     * @param isEnabled turn on/off
     * @param handlerMappingsMap handler collection
     */
    protected void triggerHandlerMappingAsyn(AbilityKey key, boolean isEnabled,
            Map<AbilityKey, List<HandlerWithPriority>> handlerMappingsMap) {
        simpleThreadPool.execute(() -> doTriggerSyn(key, isEnabled, handlerMappingsMap));
    }
    
    /**
     * Invoke componments which linked to ability key syn.
     *
     * @param key ability key from {@link AbstractAbilityRegistry}
     * @param isEnabled turn on/off
     * @param handlerMappingsMap handler collection
     */
    protected void doTriggerSyn(AbilityKey key, boolean isEnabled,
            Map<AbilityKey, List<HandlerWithPriority>> handlerMappingsMap) {
        List<HandlerWithPriority> handlerWithPriorities = handlerMappingsMap.get(key);
        // return if empty
        if (CollectionUtils.isEmpty(handlerWithPriorities)) {
            return;
        }
        Collections.sort(handlerWithPriorities);
        // invoked all
        handlerWithPriorities.forEach(handlerMappingWithPriorities -> {
            // any error from current handler does not affect other handler
            HandlerMapping handlerMapping = handlerMappingWithPriorities.handlerMapping;
            try {
                if (isEnabled) {
                    handlerMapping.enable();
                } else {
                    handlerMapping.disable();
                }
            } catch (Throwable t) {
                LOGGER.warn("[HandlerMapping] Failed to invoke {} :{}", handlerMapping.getClass().getSimpleName(),
                        t.getLocalizedMessage());
            }
        });
    }
    
    /**
     * A legal nacos application has a ability control manager.
     * If there are more than one, the one with higher priority is preferred
     *
     * @return priority
     */
    public abstract int getPriority();
    
    @JustForTest
    protected Map<AbilityKey, List<HandlerWithPriority>> handlerMapping() {
        return this.handlerMappings;
    }
    
    /**
     * Support priority handler.
     */
    protected class HandlerWithPriority implements Comparable<HandlerWithPriority> {
        
        /**.
         * Decorated
         */
        public HandlerMapping handlerMapping;
    
        /**.
         * the higher the priority, the faster it will be called
         */
        public int priority;
    
        public HandlerWithPriority(HandlerMapping handlerMapping, int priority) {
            this.handlerMapping = handlerMapping;
            this.priority = priority;
        }
    
        @Override
        public int compareTo(HandlerWithPriority o) {
            return o.priority - this.priority;
        }
    }
    
    /**.
     * notify when current node ability changing
     */
    public class AbilityUpdateEvent extends Event {
        
        private static final long serialVersionUID = -1232411212311111L;
        
        private AbilityKey abilityKey;
        
        private boolean isOn;
        
        private Map<AbilityKey, Boolean> table;
    
        private AbilityUpdateEvent(){}
    
        public Map<AbilityKey, Boolean> getAbilityTable() {
            return table;
        }
    
        public void setTable(Map<AbilityKey, Boolean> abilityTable) {
            this.table = abilityTable;
        }
        
        public AbilityKey getAbilityKey() {
            return abilityKey;
        }
    
        public void setAbilityKey(AbilityKey abilityKey) {
            this.abilityKey = abilityKey;
        }
    
        public boolean isOn() {
            return isOn;
        }
    
        public void setOn(boolean on) {
            isOn = on;
        }
    }
}
