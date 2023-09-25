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
import com.alibaba.nacos.api.ability.initializer.AbilityPostProcessor;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * It is a capability control center, manager current node abilities or other control.
 *
 * @author Daydreamer
 * @date 2022/7/12 19:18
 **/
public abstract class AbstractAbilityControlManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAbilityControlManager.class);
    
    /**.
     * ability current node running
     */
    protected final Map<String, Boolean> currentRunningAbility = new ConcurrentHashMap<>();
    
    protected AbstractAbilityControlManager() {
        ThreadUtils.addShutdownHook(this::destroy);
        NotifyCenter.registerToPublisher(AbilityUpdateEvent.class, 16384);
        currentRunningAbility.putAll(getAbilityTable());
    }

    /**
     * initialize abilities.
     *
     * @return abilities
     */
    private Map<String, Boolean> getAbilityTable() {
        // get processors
        Collection<AbilityPostProcessor> processors = NacosServiceLoader.load(AbilityPostProcessor.class);
        Map<AbilityKey, Boolean> abilities = initCurrentNodeAbilities();
        // get abilities
        for (AbilityPostProcessor processor : processors) {
            processor.process(abilities);
        }
        return AbilityKey.mapStr(abilities);
    }
    
    /**
     * Turn on the ability whose key is <p>abilityKey</p>.
     *
     * @param abilityKey ability key{@link AbilityKey}
     */
    public void enableCurrentNodeAbility(AbilityKey abilityKey) {
        doTurn(this.currentRunningAbility, abilityKey, true);
    }

    protected void doTurn(Map<String, Boolean> abilities, AbilityKey key, boolean turn) {
        abilities.put(key.getName(), turn);
        // notify event
        AbilityUpdateEvent abilityUpdateEvent = new AbilityUpdateEvent();
        abilityUpdateEvent.setTable(Collections.unmodifiableMap(currentRunningAbility));
        abilityUpdateEvent.isOn = turn;
        abilityUpdateEvent.abilityKey = key;
        NotifyCenter.publishEvent(abilityUpdateEvent);
    }
    
    /**
     * Turn off the ability whose key is <p>abilityKey</p> {@link AbilityKey}.
     *
     * @param abilityKey ability key
     */
    public void disableCurrentNodeAbility(AbilityKey abilityKey) {
        doTurn(this.currentRunningAbility, abilityKey, false);
    }
    
    /**.
     * Whether current node support
     *
     * @param abilityKey ability key from {@link AbilityKey}
     * @return whether support
     */
    public boolean isCurrentNodeAbilityRunning(AbilityKey abilityKey) {
        return currentRunningAbility.getOrDefault(abilityKey.getName(), false);
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
    public Map<String, Boolean> getCurrentNodeAbilities() {
        return Collections.unmodifiableMap(currentRunningAbility);
    }
    
    /**.
     * Close
     */
    public final void destroy() {
        LOGGER.warn("[DefaultAbilityControlManager] - Start destroying...");
        // hook
        doDestroy();
        LOGGER.warn("[DefaultAbilityControlManager] - Destruction of the end");
    }
    
    /**.
     * hook for subclass
     */
    protected void doDestroy() {
        // for server ability manager
    }
    
    /**
     * A legal nacos application has a ability control manager.
     * If there are more than one, the one with higher priority is preferred
     *
     * @return priority
     */
    public abstract int getPriority();

    /**
     * notify when current node ability changing.
     */
    public class AbilityUpdateEvent extends Event {
        
        private static final long serialVersionUID = -1232411212311111L;
        
        private AbilityKey abilityKey;
        
        private boolean isOn;
        
        private Map<String, Boolean> table;
    
        private AbilityUpdateEvent(){}
    
        public Map<String, Boolean> getAbilityTable() {
            return table;
        }
    
        public void setTable(Map<String, Boolean> abilityTable) {
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
