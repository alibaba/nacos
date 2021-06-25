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

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Naming client gprc connection event listener.
 *
 * <p>
 * When connection reconnect to server, redo the register and subscribe.
 * </p>
 *
 * @author xiweng.yy
 */
public class NamingGrpcConnectionEventListener implements ConnectionEventListener {
    
    private final NamingGrpcClientProxy clientProxy;
    
    private final ConcurrentMap<String, Instance> registeredInstanceCached = new ConcurrentHashMap<>();
    
    private final Set<String> subscribes = new ConcurrentHashSet<String>();
    
    private volatile boolean connected = false;
    
    private static final long DEFAULT_REDO_DELAY = 3000L;
    
    private static final int DEFAULT_REDO_THREAD = 2;
    
    private ScheduledExecutorService redoExecutorService;
    
    public NamingGrpcConnectionEventListener(NamingGrpcClientProxy clientProxy) {
        this.clientProxy = clientProxy;
        this.redoExecutorService = new ScheduledThreadPoolExecutor(DEFAULT_REDO_THREAD, r -> {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.naming.grpc.event.listener");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void onConnected() {
        connected = true;
        LogUtils.NAMING_LOGGER.info("Grpc re-connect, redo subscribe services");
        redoSubscribe(subscribes);
        LogUtils.NAMING_LOGGER.info("Grpc re-connect, redo register services");
        redoRegisterEachService(registeredInstanceCached.keySet());
    }
    
    private void redoSubscribe(Set<String> subscribes) {
        Set<String> failedSubscribes = new ConcurrentHashSet<>();
        for (String each : subscribes) {
            if (!connected) {
                failedSubscribes.clear();
                break;
            }
            ServiceInfo serviceInfo = ServiceInfo.fromKey(each);
            try {
                clientProxy.subscribe(serviceInfo.getName(), serviceInfo.getGroupName(), serviceInfo.getClusters());
            } catch (NacosException e) {
                failedSubscribes.add(each);
                LogUtils.NAMING_LOGGER.warn(String.format("re subscribe service %s failed, try again later.", serviceInfo.getName()), e);
            }
        }
        if (!failedSubscribes.isEmpty()) {
            redoExecutorService.schedule(() -> redoSubscribe(failedSubscribes), DEFAULT_REDO_DELAY, TimeUnit.MILLISECONDS);
        }
    }
    
    private void redoRegisterEachService(Set<String> services) {
        Set<String> failedServices = new ConcurrentHashSet<>();
        for (String each : services) {
            if (!connected) {
                failedServices.clear();
                break;
            }
            String serviceName = NamingUtils.getServiceName(each);
            String groupName = NamingUtils.getGroupName(each);
            Instance instance = registeredInstanceCached.get(each);
            if (!redoRegisterEachInstance(serviceName, groupName, instance)) {
                failedServices.add(each);
            }
        }
        if (!failedServices.isEmpty()) {
            redoExecutorService.schedule(() -> redoRegisterEachService(failedServices), DEFAULT_REDO_DELAY, TimeUnit.MILLISECONDS);
        }
    }
    
    private boolean redoRegisterEachInstance(String serviceName, String groupName, Instance instance) {
        try {
            clientProxy.registerService(serviceName, groupName, instance);
        } catch (NacosException e) {
            LogUtils.NAMING_LOGGER.warn(String
                    .format("redo register for service %s@@%s, %s failed, try again later.", groupName, serviceName, instance.toString()), e);
            return false;
        }
        return true;
    }
    
    @Override
    public void onDisConnect() {
        connected = false;
        LogUtils.NAMING_LOGGER.warn("Grpc connection disconnect");
    }
    
    /**
     * Cache registered instance for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    registered instance
     */
    public void cacheInstanceForRedo(String serviceName, String groupName, Instance instance) {
        String key = NamingUtils.getGroupedName(serviceName, groupName);
        registeredInstanceCached.put(key, instance);
    }
    
    /**
     * Remove registered instance for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    registered instance
     */
    public void removeInstanceForRedo(String serviceName, String groupName, Instance instance) {
        String key = NamingUtils.getGroupedName(serviceName, groupName);
        registeredInstanceCached.remove(key);
    }
    
    public void cacheSubscriberForRedo(String fullServiceName, String cluster) {
        subscribes.add(ServiceInfo.getKey(fullServiceName, cluster));
    }
    
    public void removeSubscriberForRedo(String fullServiceName, String cluster) {
        subscribes.remove(ServiceInfo.getKey(fullServiceName, cluster));
    }
    
    public void shutdown() {
        LogUtils.NAMING_LOGGER.info("Shutdown grpc event listener executor " + redoExecutorService);
        redoExecutorService.shutdownNow();
    }
    
}
