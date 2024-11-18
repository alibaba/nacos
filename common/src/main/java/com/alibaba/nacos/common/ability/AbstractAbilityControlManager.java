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
import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.initializer.AbilityPostProcessor;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * It is a capability control center, manager current node abilities or other control.
 *
 * @author Daydreamer
 * @date 2022/7/12 19:18
 **/
public abstract class AbstractAbilityControlManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAbilityControlManager.class);
    
    /**
     * current node support abilities.
     */
    protected final Map<AbilityMode, Map<String, Boolean>> currentNodeAbilities = new ConcurrentHashMap<>();
    
    protected AbstractAbilityControlManager() {
        NotifyCenter.registerToPublisher(AbilityUpdateEvent.class, 16384);
        initAbilityTable();
    }
    
    /**
     * initialize abilities.
     *
     * @return abilities
     */
    private void initAbilityTable() {
        LOGGER.info("Ready to get current node abilities...");
        // get processors
        Map<AbilityMode, Map<AbilityKey, Boolean>> abilities = initCurrentNodeAbilities();
        // get abilities
        for (AbilityMode mode : AbilityMode.values()) {
            Map<AbilityKey, Boolean> abilitiesTable = abilities.get(mode);
            if (abilitiesTable == null) {
                continue;
            }
            // check whether exist error key
            // check for developer
            for (AbilityKey abilityKey : abilitiesTable.keySet()) {
                if (!mode.equals(abilityKey.getMode())) {
                    LOGGER.error(
                            "You should not contain a other mode: {} in a specify mode: {} abilities set, error key: {}, please check again.",
                            abilityKey.getMode(), mode, abilityKey);
                    throw new IllegalStateException(
                            "Except mode: " + mode + " but " + abilityKey + " mode: " + abilityKey.getMode()
                                    + ", please check again.");
                }
            }
            Collection<AbilityPostProcessor> processors = NacosServiceLoader.load(AbilityPostProcessor.class);
            for (AbilityPostProcessor processor : processors) {
                processor.process(mode, abilitiesTable);
            }
        }
        // init
        Set<AbilityMode> abilityModes = abilities.keySet();
        LOGGER.info("Ready to initialize current node abilities, support modes: {}", abilityModes);
        for (AbilityMode abilityMode : abilityModes) {
            this.currentNodeAbilities
                    .put(abilityMode, new ConcurrentHashMap<>(AbilityKey.mapStr(abilities.get(abilityMode))));
        }
        LOGGER.info("Initialize current abilities finish...");
    }
    
    /**
     * Turn on the ability whose key is <p>abilityKey</p>.
     *
     * @param abilityKey ability key{@link AbilityKey}
     */
    public void enableCurrentNodeAbility(AbilityKey abilityKey) {
        Map<String, Boolean> abilities = this.currentNodeAbilities.get(abilityKey.getMode());
        if (abilities != null) {
            doTurn(abilities, abilityKey, true);
        }
    }
    
    protected void doTurn(Map<String, Boolean> abilities, AbilityKey key, boolean turn) {
        LOGGER.info("Turn current node ability: {}, turn: {}", key, turn);
        abilities.put(key.getName(), turn);
        // notify event
        AbilityUpdateEvent abilityUpdateEvent = new AbilityUpdateEvent();
        abilityUpdateEvent.setTable(Collections.unmodifiableMap(abilities));
        abilityUpdateEvent.setOn(turn);
        abilityUpdateEvent.setAbilityKey(key);
        NotifyCenter.publishEvent(abilityUpdateEvent);
    }
    
    /**
     * Turn off the ability whose key is <p>abilityKey</p> {@link AbilityKey}.
     *
     * @param abilityKey ability key
     */
    public void disableCurrentNodeAbility(AbilityKey abilityKey) {
        Map<String, Boolean> abilities = this.currentNodeAbilities.get(abilityKey.getMode());
        if (abilities != null) {
            doTurn(abilities, abilityKey, false);
        }
    }
    
    /**
     * . Whether current node support
     *
     * @param abilityKey ability key from {@link AbilityKey}
     * @return whether support
     */
    public AbilityStatus isCurrentNodeAbilityRunning(AbilityKey abilityKey) {
        Map<String, Boolean> abilities = currentNodeAbilities.get(abilityKey.getMode());
        if (abilities != null) {
            Boolean support = abilities.get(abilityKey.getName());
            if (support != null) {
                return support ? AbilityStatus.SUPPORTED : AbilityStatus.NOT_SUPPORTED;
            }
        }
        return AbilityStatus.UNKNOWN;
    }
    
    /**
     * . Init current node abilities
     *
     * @return current node abilities
     */
    protected abstract Map<AbilityMode, Map<AbilityKey, Boolean>> initCurrentNodeAbilities();
    
    /**
     * . Return the abilities current node
     *
     * @return current abilities
     */
    public Map<String, Boolean> getCurrentNodeAbilities(AbilityMode mode) {
        Map<String, Boolean> abilities = currentNodeAbilities.get(mode);
        if (abilities != null) {
            return Collections.unmodifiableMap(abilities);
        }
        return Collections.emptyMap();
    }
    
    /**
     * A legal nacos application has a ability control manager. If there are more than one, the one with higher priority
     * is preferred
     *
     * @return priority
     */
    public abstract int getPriority();
    
    /**
     * notify when current node ability changing.
     */
    public static class AbilityUpdateEvent extends Event {
        
        private static final long serialVersionUID = -1232411212311111L;
        
        private AbilityKey abilityKey;
        
        private boolean isOn;
        
        private Map<String, Boolean> table;
        
        private AbilityUpdateEvent() {
        }
        
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
