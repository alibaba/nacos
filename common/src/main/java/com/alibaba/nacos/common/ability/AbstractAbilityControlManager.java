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
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.common.ability.handler.AbilityHandlePreProcessor;
import com.alibaba.nacos.common.ability.inter.TraceableAbilityControlManager;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**.
 * @author Daydreamer
 * @description Base class for ability control. It can only be used internally by Nacos.It showld be sington.
 * @date 2022/7/12 19:18
 **/
@SuppressWarnings("all")
public abstract class AbstractAbilityControlManager implements TraceableAbilityControlManager {


    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAbilityControlManager.class);

    /**
     * Abilities current supporting
     * <p>
     * key: ability key from {@link AbilityKey}
     * value: whether to turn on
     */
    protected final Map<String, Boolean> currentRunningAbility = new ConcurrentHashMap<>();

    /**
     * Ability table collections
     * <p>
     * key: connectionId
     * value: AbilityTable
     */
    protected final Map<String, AbilityTable> nodeAbilityTable = new ConcurrentHashMap<>();
    
    /**.
     * These handlers will be invoke before combine the ability table
     */
    private final List<AbilityHandlePreProcessor> abilityHandlePreProcessors = new ArrayList<>();

    /**
     * This map is used to trace the status of ability table.
     * Its status should be update after {@link #addNewTable(AbilityTable)} and {@link #removeTable(String)}
     */
    protected final Map<String, AtomicReference<AbilityStatus>> abilityStatus = new ConcurrentHashMap<>();
    
    private final ReentrantLock lockForProcessors = new ReentrantLock();
    
    private final ReentrantLock lockForAbilityTable = new ReentrantLock();
    
    protected AbstractAbilityControlManager() {
        // register events
        registerAbilityEvent();
        // put abilities
        currentRunningAbility.putAll(AbilityKey.getCurrentNodeSupportAbility());
        // initialize
        init();
    }


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
    public boolean isCurrentNodeAbilityRunning(String abilityKey) {
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
            // update status
            abilityStatus.put(connectionId, new AtomicReference<>(AbilityStatus.INITIALIZING));
            // handle ability table before joining current node
            AbilityTable processed = process(table);
            // hook method
            add(processed);
            // add to node
            nodeAbilityTable.put(connectionId, table);
        } finally {
            lockForAbilityTable.unlock();
        }
        // update status
        AtomicReference<AbilityStatus> abilityStatusAtomicReference = abilityStatus.get(table.getConnectionId());
        if (abilityStatusAtomicReference != null) {
            // try one time
            // do nothing if AbilityStatus == Expired
            // if ready
            if(abilityStatusAtomicReference.compareAndSet(AbilityStatus.INITIALIZING, AbilityStatus.READY)) {
                // publish event to subscriber
                AbilityComeEvent updateEvent = new AbilityComeEvent();
                updateEvent.setConnectionId(table.getConnectionId());
                updateEvent.setTable(table);
                NotifyCenter.publishEvent(updateEvent);
            }
        } else {
            LOGGER.warn("[AbiityControlManager] Cannot get connection status after processing ability table, possible reason is that the network is unstable");
        }
    }

    /**
     * Remove a ability table
     *
     * @param connectionId the ability table which is removing.
     */
    @Override
    public final void removeTable(String connectionId) {
        // if not exists
        if(connectionId == null || !nodeAbilityTable.containsKey(connectionId)){
            return;
        }
        AbilityTable removingTable = null;
        lockForAbilityTable.lock();
        try {
            // check
            if (!nodeAbilityTable.containsKey(connectionId)) {
                return;
            }
            nodeAbilityTable.get(connectionId);
            // update status
            abilityStatus.computeIfPresent(connectionId, (k, v) -> {
                v.set(AbilityStatus.EXPIRED);
                return v;
            });
            // hook method
            remove(connectionId);
            // remove
            nodeAbilityTable.remove(connectionId);
        } finally {
            lockForAbilityTable.unlock();
        }
        // remove status
        abilityStatus.remove(connectionId);
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
     * Get the status of the ability table.
     *
     * @param connectionId connection id
     * @return status of ability table {@link AbilityStatus}
     */
    @Override
    public AbilityStatus trace(String connectionId) {
        if (connectionId == null) {
            return AbilityStatus.NOT_EXIST;
        }
        return abilityStatus.getOrDefault(connectionId, new AtomicReference<>(AbilityStatus.NOT_EXIST)).get();
    }

    /**
     * Trace the status of connection if <p>{@link AbilityStatus#INITIALIZING}<p/>, wake up if <p>{@link AbilityStatus#READY}<p/>
     * It will return if status is <p>{@link AbilityStatus#EXPIRED}<p/> or <p>{@link AbilityStatus#NOT_EXIST}<p/>
     *
     * @param connectionId connection id
     * @param source       source status
     * @param target       target status
     * @return if success
     */
    @Override
    public boolean traceReadySyn(String connectionId) {
        AbilityStatus source = AbilityStatus.INITIALIZING;
        AbilityStatus target = AbilityStatus.READY;
        AtomicReference<AbilityStatus> atomicReference = abilityStatus.get(connectionId);
        // return if null
        if (atomicReference == null || atomicReference.get().equals(AbilityStatus.EXPIRED)) {
            return false;
        } else if (target == atomicReference.get()) {
            return true;
        }
        // try if status legal
        while (!atomicReference.get().equals(target) && atomicReference.get().equals(source)) {
            LockSupport.parkNanos(100L);
            // if expired
            if (atomicReference.get().equals(AbilityStatus.EXPIRED)) {
                return false;
            }
        }
        return atomicReference.get().equals(target);
    }
    
    /**.
     * Invoking {@link AbilityHandlePreProcessor}
     *
     * @param source source ability table
     * @return result
     */
    protected AbilityTable process(AbilityTable source) {
        // do nothing if no processor
        if (CollectionUtils.isEmpty(abilityHandlePreProcessors)) {
            return source;
        }
        // copy to advoid error process
        AbilityTable abilityTable = source;
        AbilityTable copy = new AbilityTable(source.getConnectionId(), new HashMap<>(source.getAbility()), source.isServer(), source.getVersion());
        for (AbilityHandlePreProcessor handler : abilityHandlePreProcessors) {
            try {
                abilityTable = handler.handle(abilityTable);
            } catch (Throwable t) {
                LOGGER.warn("[AbilityHandlePostProcessor] Failed to invoke {} :{}",
                        handler.getClass().getSimpleName(), t.getLocalizedMessage());
                // ensure normal operation
                abilityTable = copy;
            }
        }
        return abilityTable;
    }
    
    /**.
     * They will be invoked before updating ability table, but the order in which
     * they are called cannot be guaranteed
     *
     * @param postProcessor PostProcessor instance
     */
    @Override
    public void addPostProcessor(AbilityHandlePreProcessor postProcessor) {
        lockForProcessors.lock();
        try {
            abilityHandlePreProcessors.add(postProcessor);
        } finally {
            lockForProcessors.unlock();
            LOGGER.info("[AbilityHandlePostProcessor] registry handler: {}",
                    postProcessor.getClass().getSimpleName());
        }
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
    public Map<String, Boolean> getCurrentRunningAbility() {
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
