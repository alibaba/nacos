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
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.DefaultAbilityControlManager;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.core.ability.config.AbilityConfigs;
import com.alibaba.nacos.core.ability.inte.ClusterAbilityControlSupport;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**.
 * @author Daydreamer
 * @description {@link AbstractAbilityControlManager} for nacos-server.
 * @date 2022/7/13 21:14
 **/
public class ServerAbilityControlManager extends DefaultAbilityControlManager implements ClusterAbilityControlSupport {
    
    /**.
     * ability for cluster
     */
    private final Map<AbilityKey, Boolean> clusterAbilityTable = new ConcurrentHashMap<>();

    /**.
     * ability for server
     */
    private final Map<String, AbilityTable> serversAbilityTable = new ConcurrentHashMap<>();
    
    /**.
     * Number of servers that do not support capability negotiation
     */
    private final ConcurrentHashSet<String> serverNoAbilityNegotiation = new ConcurrentHashSet<>();
    
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
        // static abilities
        Map<AbilityKey, Boolean> staticAbilities = ServerAbilities.getStaticAbilities();
        // all function server can support
        Set<AbilityKey> abilityKeys = staticAbilities.keySet();
        Map<AbilityKey, Boolean> abilityTable = new HashMap<>(abilityKeys.size());
        // if not define in config, then load from ServerAbilities
        Set<AbilityKey> unIncludedInConfig = new HashSet<>();
        abilityKeys.forEach(abilityKey -> {
            String key = AbilityConfigs.PREFIX + abilityKey.getName();
            try {
                Boolean property = EnvUtil.getProperty(key, Boolean.class);
                // if not null
                if (property != null) {
                    abilityTable.put(abilityKey, property);
                } else {
                    unIncludedInConfig.add(abilityKey);
                }
            } catch (Exception e) {
                // from ServerAbilities
                unIncludedInConfig.add(abilityKey);
            }
        });
        // load from ServerAbilities
        unIncludedInConfig.forEach(abilityKey -> abilityTable.put(abilityKey, staticAbilities.get(abilityKey)));
        return abilityTable;
    }
    
    @Override
    public AbilityStatus isSupport(String connectionId, AbilityKey abilityKey) {
        AbilityTable abilityTable = nodeAbilityTable.get(connectionId);
        if (abilityTable == null) {
            return AbilityStatus.UNKNOWN;
        }
        Boolean isSupport = Optional.ofNullable(abilityTable.getAbility()).orElse(Collections.emptyMap())
                .getOrDefault(abilityKey, false);
        return isSupport ? AbilityStatus.SUPPORTED : AbilityStatus.NOT_SUPPORTED;
    }
    
    /**.
     * Whether all the servers currently connected support a certain capability
     *
     * @param abilityKey ability key
     * @return whether it is turn on
     */
    @Override
    public AbilityStatus isClusterEnableAbilityNow(AbilityKey abilityKey) {
        if (serverNoAbilityNegotiation.size() > 0) {
            return AbilityStatus.UNKNOWN;
        }
        return clusterAbilityTable.getOrDefault(abilityKey, Boolean.FALSE) ? AbilityStatus.SUPPORTED : AbilityStatus.NOT_SUPPORTED;
    }
    
    @Override
    public Map<AbilityKey, Boolean> getClusterAbility() {
        return serverNoAbilityNegotiation.size() > 0 ? null : Collections.unmodifiableMap(clusterAbilityTable);
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
                        clusterAbilityTable.replace(abilityKey, false);
                        // notify
                        NotifyCenter.publishEvent(buildClusterEvent(abilityKey, false));
                    }
                });
            }
        } else if (isServer && table.getAbility() == null) {
            // add mark if server doesn't support ability table
            serverNoAbilityNegotiation.add(table.getConnectionId());
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
        // if not support
        serverNoAbilityNegotiation.remove(connectionId);
        // return if null
        if (abilityTable == null) {
            return;
        }
        // from which env
        if (abilityTable.isServer()) {
            // remove from server ability collection if support
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
                        // notify
                        NotifyCenter.publishEvent(buildClusterEvent(abilityKey, newVal));
                    }
                });
            }
        }
    }
    
    @Override
    public int getPriority() {
        return 1;
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
    protected Set<String> serverNotSupport() {
        return serverNoAbilityNegotiation;
    }

}
