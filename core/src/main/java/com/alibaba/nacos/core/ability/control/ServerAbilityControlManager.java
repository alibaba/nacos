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

package com.alibaba.nacos.core.ability.control;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.api.ability.register.impl.ServerAbilities;
import com.alibaba.nacos.api.utils.AbilityTableUtils;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.DefaultAbilityControlManager;
import com.alibaba.nacos.common.ability.handler.HandlerMapping;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.core.ability.inte.ClusterAbilityControlSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**.
 * @author Daydreamer
 * @description {@link AbstractAbilityControlManager} for nacos-server.
 * @date 2022/7/13 21:14
 **/
public class ServerAbilityControlManager extends DefaultAbilityControlManager implements ClusterAbilityControlSupport {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerAbilityControlManager.class);

    /**.
     * ability for cluster
     */
    private final Map<AbilityKey, Boolean> clusterAbilityTable = new ConcurrentHashMap<>();

    /**.
     * ability for server
     */
    private final Map<String, AbilityTable> serversAbilityTable = new ConcurrentHashMap<>();

    /**
     * components for cluster. these will be invoked if cluster ability table changes.
     */
    private final Map<AbilityKey, List<HandlerWithPriority>> clusterHandlerMapping = new ConcurrentHashMap<>();
    
    private Lock lockForClusterComponents = new ReentrantLock();
    
    public ServerAbilityControlManager() {
        // add current node into
        AbilityTable currentNodeAbility = new AbilityTable();
        currentNodeAbility.setAbility(super.currentRunningAbility);
        currentNodeAbility.setConnectionId("current-node");
        serversAbilityTable.put(currentNodeAbility.getConnectionId(), currentNodeAbility);
        clusterAbilityTable.putAll(currentNodeAbility.getAbility());
        NotifyCenter.registerToPublisher(ClusterAbilityUpdateEvent.class, 16384);
    }
    
    @Override
    protected Map<AbilityKey, Boolean> getCurrentNodeSupportAbility() {
        return AbilityTableUtils.getAbilityTableBy(ServerAbilities.getBitFlags(), ServerAbilities.getOffset());
    }
    
    @Override
    public AbilityStatus isSupport(String connectionId, AbilityKey abilityKey) {
        Boolean isRunning = currentRunningAbility.getOrDefault(abilityKey, false);
        if (!isRunning) {
            return AbilityStatus.NOT_SUPPORTED;
        }
        AbilityTable abilityTable = nodeAbilityTable.get(connectionId);
        if(abilityTable == null) {
            return AbilityStatus.UNKNOWN;
        }
        Boolean isSupport = Optional.ofNullable(abilityTable.getAbility()).orElse(Collections.emptyMap())
                .getOrDefault(abilityKey, false);
        return isSupport ? AbilityStatus.SUPPORTED : AbilityStatus.NOT_SUPPORTED;
    }
    
    /**.
     * Whether current cluster supports ability
     *
     * @param abilityKey ability key
     * @return whether it is turn on
     */
    @Override
    public boolean isClusterEnableAbility(AbilityKey abilityKey) {
        return clusterAbilityTable.getOrDefault(abilityKey, Boolean.FALSE);
    }
    
    @Override
    public Map<AbilityKey, Boolean> getClusterAbility() {
        return Collections.unmodifiableMap(clusterAbilityTable);
    }
    
    /**.
     * Register components for cluster. These will be trigger when its interested ability changes
     *
     * @param abilityKey     ability key
     * @param priority       the higher the priority, the faster it will be called
     * @param handlerMapping component
     */
    @Override
    public void registerComponentForCluster(AbilityKey abilityKey, HandlerMapping handlerMapping, int priority) {
        doRegisterComponent(abilityKey, handlerMapping, this.clusterHandlerMapping, lockForClusterComponents, priority, clusterAbilityTable);
    }
    
    @Override
    public int removeClusterComponent(AbilityKey abilityKey, Class<? extends HandlerMapping> handlerMappingClazz) {
        return doRemove(abilityKey, handlerMappingClazz, lockForClusterComponents, clusterHandlerMapping);
    }
    
    @Override
    public int removeAllForCluster(AbilityKey abilityKey) {
        List<HandlerWithPriority> remove = this.clusterHandlerMapping.remove(abilityKey);
        return Optional.ofNullable(remove).orElse(Collections.emptyList()).size();
    }
    
    @Override
    protected void add(AbilityTable table) {
        // from which env
        boolean isServer = table.isServer();
        // if not null
        if (table.getConnectionId() != null && table.getAbility() != null) {
            if (isServer) {
                serversAbilityTable.put(table.getConnectionId(), table);
                // enter cluster
                Map<AbilityKey, Boolean> nodeAbility = table.getAbility();
                Set<AbilityKey> keySet = clusterAbilityTable.keySet();
                keySet.forEach(abilityKey -> {
                    Boolean isEnabled = clusterAbilityTable.get(abilityKey);
                    Boolean val = nodeAbility.getOrDefault(abilityKey, Boolean.FALSE);
                    // new res
                    Boolean newRes = val && isEnabled;
                    // if ability changes
                    if (!newRes.equals(isEnabled)) {
                        triggerHandlerMappingAsyn(abilityKey, false, this.clusterHandlerMapping);
                        clusterAbilityTable.replace(abilityKey, false);
                        // notify
                        NotifyCenter.publishEvent(buildClusterEvent(abilityKey, false));
                    }
                });
            }
        }
    }
    
    private ClusterAbilityUpdateEvent buildClusterEvent(AbilityKey abilityKey, boolean isOn) {
        // notify
        ClusterAbilityUpdateEvent event = new ClusterAbilityUpdateEvent();
        event.setAbilityKey(abilityKey);
        event.setOn(isOn);
        event.setTable(new AbilityTable().setAbility(Collections.unmodifiableMap(clusterAbilityTable)));
        return event;
    }

    @Override
    protected void remove(String connectionId) {
        // from which
        AbilityTable abilityTable = nodeAbilityTable.get(connectionId);
        // return if null
        if (abilityTable == null) {
            return;
        }
        // from which env
        if (abilityTable.isServer()) {
            // remove from server ability collection
            serversAbilityTable.remove(connectionId);
            // remove from cluster
            if (MapUtil.isNotEmpty(serversAbilityTable)) {
                Set<AbilityKey> keySet = clusterAbilityTable.keySet();
                keySet.forEach(abilityKey -> {
                    Boolean isEnabled = clusterAbilityTable.getOrDefault(abilityKey, Boolean.FALSE);
                    // nothing to do if enabled
                    if (isEnabled) {
                        return;
                    }
                    // recalculate
                    Boolean newVal = serversAbilityTable.values()
                            .stream()
                            .map(AbilityTable::getAbility)
                            .map((map) -> map.getOrDefault(abilityKey, Boolean.FALSE))
                            .reduce((a, b) -> a && b)
                            .orElse(Boolean.FALSE);
                    clusterAbilityTable.replace(abilityKey, newVal);
                    // if change
                    if (!isEnabled.equals(newVal)) {
                        triggerHandlerMappingAsyn(abilityKey, newVal, this.clusterHandlerMapping);
                        // notify
                        NotifyCenter.publishEvent(buildClusterEvent(abilityKey, newVal));
                    }
                });
            }
        }
    }
    
    @Override
    protected void doDestroy() {
        if (MapUtil.isNotEmpty(clusterHandlerMapping)) {
            if (MapUtil.isNotEmpty(clusterHandlerMapping)) {
                clusterHandlerMapping.keySet().forEach(key -> doTriggerSyn(key, false, clusterHandlerMapping));
            }
        }
        LOGGER.warn("[ServerAbilityControlManager] - Destruction of the end");
    }
    
    /**.
     * notify when current node ability changing
     */
    public class ClusterAbilityUpdateEvent extends AbilityEvent {
        
        private static final long serialVersionUID = -122222411212200111L;
        
        private AbilityKey abilityKey;
        
        private boolean isOn;
        
        private ClusterAbilityUpdateEvent(){}
        
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
    
    @JustForTest
    protected void setClusterAbilityTable(Map<AbilityKey, Boolean> map) {
        clusterAbilityTable.putAll(map);
    }

    @JustForTest
    protected Map<AbilityKey, List<HandlerWithPriority>> clusterHandlerMapping() {
        return this.clusterHandlerMapping;
    }

}
