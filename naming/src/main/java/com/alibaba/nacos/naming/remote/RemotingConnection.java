/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote;

import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remoting Connection Instance.
 *
 * @author xiweng.yy
 */
public class RemotingConnection {
    
    private final ConcurrentMap<String, Set<Subscriber>> subscriberIndex = new ConcurrentHashMap<>();
    
    private final ConcurrentMap<String, Set<Instance>> instanceIndex = new ConcurrentHashMap<>();
    
    private final Connection connection;
    
    private long lastHeartBeatTime;
    
    public RemotingConnection(Connection connection) {
        this.connection = connection;
        this.lastHeartBeatTime = System.currentTimeMillis();
    }
    
    public String getConnectionId() {
        return connection.getConnectionId();
    }
    
    /**
     * Add new instance.
     *
     * @param namespaceId     namespace Id
     * @param fullServiceName full service name with group name
     * @param instance        instance
     */
    public void addNewInstance(String namespaceId, String fullServiceName, Instance instance) {
        String key = KeyBuilder.buildServiceMetaKey(namespaceId, fullServiceName);
        if (!instanceIndex.containsKey(key)) {
            instanceIndex.put(key, new ConcurrentHashSet<>());
        }
        instanceIndex.get(key).add(instance);
    }
    
    /**
     * Remove instance.
     *
     * @param namespaceId     namespace Id
     * @param fullServiceName full service name with group name
     * @param instance        instance
     */
    public void removeInstance(String namespaceId, String fullServiceName, Instance instance) {
        String key = KeyBuilder.buildServiceMetaKey(namespaceId, fullServiceName);
        if (!instanceIndex.containsKey(key)) {
            return;
        }
        instanceIndex.get(key).remove(instance);
    }
    
    /**
     * Add new subscriber.
     *
     * @param namespaceId     namespace Id
     * @param fullServiceName full service name with group name
     * @param subscriber      subscriber
     */
    public void addNewSubscriber(String namespaceId, String fullServiceName, Subscriber subscriber) {
        String key = UtilsAndCommons.assembleFullServiceName(namespaceId, fullServiceName);
        if (!subscriberIndex.containsKey(key)) {
            subscriberIndex.put(key, new ConcurrentHashSet<>());
        }
        subscriberIndex.get(key).add(subscriber);
    }
    
    /**
     * Remove subscriber.
     *
     * @param namespaceId     namespace Id
     * @param fullServiceName full service name with group name
     * @param subscriber      subscriber
     */
    public void removeSubscriber(String namespaceId, String fullServiceName, Subscriber subscriber) {
        String key = UtilsAndCommons.assembleFullServiceName(namespaceId, fullServiceName);
        if (!subscriberIndex.containsKey(key)) {
            return;
        }
        subscriberIndex.get(key).remove(subscriber);
    }
    
    public ConcurrentMap<String, Set<Subscriber>> getSubscriberIndex() {
        return subscriberIndex;
    }
    
    public ConcurrentMap<String, Set<Instance>> getInstanceIndex() {
        return instanceIndex;
    }
    
    public long getLastHeartBeatTime() {
        return lastHeartBeatTime;
    }
    
    public void setLastHeartBeatTime(long lastHeartBeatTime) {
        this.lastHeartBeatTime = lastHeartBeatTime;
    }
}
