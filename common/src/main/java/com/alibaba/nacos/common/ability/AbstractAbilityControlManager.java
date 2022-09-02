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
import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.common.ability.inter.AbilityControlManager;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**.
 * @author Daydreamer
 * @description Base class for ability control. It can only be used internally by Nacos.It showld be sington.
 * @date 2022/7/12 19:18
 **/
@SuppressWarnings("all")
public abstract class AbstractAbilityControlManager implements AbilityControlManager {


    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAbilityControlManager.class);

    /**
     * Abilities current supporting
     * <p>
     * key: ability key from {@link AbstractAbilityRegistry}
     * value: whether to turn on
     */
    protected final Map<AbilityKey, Boolean> currentRunningAbility = new ConcurrentHashMap<>();

    /**
     * Ability table collections
     * <p>
     * key: connectionId
     * value: AbilityTable
     */
    protected final Map<String, AbilityTable> nodeAbilityTable = new ConcurrentHashMap<>();
    
    private final ReentrantLock lockForProcessors = new ReentrantLock();
    
    private final ReentrantLock lockForAbilityTable = new ReentrantLock();
    
    protected AbstractAbilityControlManager() {
        // register events
        registerAbilityEvent();
        // put abilities
        currentRunningAbility.putAll(getCurrentNodeSupportAbility());
        // initialize
        init();
    }
    
    /**
     * This is a hook for subclass to init current node ability
     *
     * @return current node ability
     */
    protected abstract Map<AbilityKey, Boolean> getCurrentNodeSupportAbility();


    private void registerAbilityEvent(){
        // register events
        NotifyCenter.registerToPublisher(AbilityComeEvent.class, 16384);
        NotifyCenter.registerToPublisher(AbilityExpiredEvent.class, 16384);
    }

    /**
     * Whether the ability current node supporting is running. Return false if current node doesn't support.
     *
     * @param abilityKey ability key
     * @return is running
     */
    @Override
    public boolean isCurrentNodeAbilityRunning(AbilityKey abilityKey) {
        return currentRunningAbility.getOrDefault(abilityKey, false);
    }

    /**
     * Register a new ability table.
     *
     * @param table the ability table.
     */
    @Override
    public final void addNewTable(AbilityTable table) {
        // id should not be null
        String connectionId = table.getConnectionId();
        // if exists
        if (contains(connectionId) || connectionId == null) {
            return;
        }
        lockForAbilityTable.lock();
        try {
            // check
            if (contains(connectionId)) {
                return;
            }
            // hook method
            add(table);
            // null if not support ability table
            Map<AbilityKey, Boolean> clientAbilities = table.getAbility();
            if (clientAbilities != null) {
                // add to nod
                Set<AbilityKey> abilityKeys = table.getAbility().keySet();
                abilityKeys.forEach(abilityKey -> {
                    Boolean res = currentRunningAbility.getOrDefault(abilityKey, false);
                    Boolean coming = clientAbilities.getOrDefault(abilityKey, false);
                    clientAbilities.put(abilityKey, res && coming);
                });
                nodeAbilityTable.put(connectionId, table);
            }
        } finally {
            lockForAbilityTable.unlock();
        }
        // publish event to subscriber
        AbilityComeEvent updateEvent = new AbilityComeEvent();
        updateEvent.setConnectionId(table.getConnectionId());
        updateEvent.setTable(table);
        NotifyCenter.publishEvent(updateEvent);
    }

    /**
     * Remove a ability table
     *
     * @param connectionId the ability table which is removing.
     */
    @Override
    public final void removeTable(String connectionId) {
        AbilityTable removingTable = null;
        lockForAbilityTable.lock();
        try {
            // hook method
            remove(connectionId);
            // remove
            removingTable = nodeAbilityTable.remove(connectionId);
        } finally {
            lockForAbilityTable.unlock();
        }
        // publish event
        if (removingTable != null) {
            AbilityExpiredEvent expiredEvent = new AbilityExpiredEvent();
            expiredEvent.setTable(removingTable);
            expiredEvent.setConnectionId(connectionId);
            NotifyCenter.publishEvent(expiredEvent);
        }
    }

    /**
     * Register a new ability table. This is a ThreadSafe method for {@link AbstractAbilityControlManager#remove(String)}.
     *
     * @param table the ability table.
     */
    protected abstract void add(AbilityTable table);


    /**
     * Remove a ability table. This is a ThreadSafe method for {@link AbstractAbilityControlManager#add(AbilityTable)}.
     *
     * @param connectionId the ability table which is removing.
     */
    protected abstract void remove(String connectionId);


    /**
     * wthether contains this ability table
     *
     * @return
     */
    @Override
    public boolean contains(String connectionId) {
        return nodeAbilityTable.containsKey(connectionId);
    }
    
    /**
     * Initialize the manager
     */
    @Override
    public void init() {
        // default init
        // nothing to do
    }
    
    /**
     * It should be invoked before destroy
     */
    @Override
    public void destroy() {
        // default destroy
        // nothing to do
    }

    /**
     * Return ability table of current node
     *
     * @return ability table
     */
    @Override
    public Map<AbilityKey, Boolean> getCurrentRunningAbility() {
        return new HashMap<>(this.currentRunningAbility);
    }

    /**
     * base class for ability
     */
    public abstract class AbilityEvent extends Event {

        private static final long serialVersionUID = -123241121302761L;

        protected AbilityEvent(){}

        /**
         * connection id.
         */
        private String connectionId;

        /**
         * ability table
         */
        private AbilityTable table;


        public String getConnectionId() {
            return connectionId;
        }

        public void setConnectionId(String connectionId) {
            this.connectionId = connectionId;
        }

        public AbilityTable getTable() {
            return table;
        }

        public void setTable(AbilityTable table) {
            this.table = table;
        }
    }
    
    /**
     * when a connection connected.
     */
    public class AbilityComeEvent extends AbilityEvent {
        
        private static final long serialVersionUID = -123241121302761L;
        
        private AbilityComeEvent(){}
    }

    /**
     * when a connection disconnected.
     */
    public class AbilityExpiredEvent extends AbilityEvent {

        private static final long serialVersionUID = -123241121212127619L;

        private AbilityExpiredEvent(){}

    }
}
