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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    
    private int queueSize = 16384;
    
    private BlockingQueue<Runnable> redoQueue = new ArrayBlockingQueue<>(queueSize);
    
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
        startRedoFailedTaskThread();
    }
    
    @Override
    public void onConnected() {
        connected = true;
        redoSubscribe();
        redoRegisterEachService();
    }
    
    private void redoSubscribe() {
        LogUtils.NAMING_LOGGER.info("Grpc re-connect, redo subscribe services");
        for (String each : subscribes) {
            ServiceInfo serviceInfo = ServiceInfo.fromKey(each);
            try {
                clientProxy.subscribe(serviceInfo.getName(), serviceInfo.getGroupName(), serviceInfo.getClusters());
            } catch (NacosException e) {
                redoQueue.offer(new RedoSubscribeTask(serviceInfo.getName(), serviceInfo.getGroupName(), serviceInfo.getClusters()));
                LogUtils.NAMING_LOGGER.warn(String.format("re subscribe service %s failed, try again later.", serviceInfo.getName()), e);
            }
        }
    }
    
    private void redoRegisterEachService() {
        LogUtils.NAMING_LOGGER.info("Grpc re-connect, redo register services");
        for (Map.Entry<String, Instance> each : registeredInstanceCached.entrySet()) {
            String serviceName = NamingUtils.getServiceName(each.getKey());
            String groupName = NamingUtils.getGroupName(each.getKey());
            redoRegisterEachInstance(serviceName, groupName, each.getValue());
        }
    }
    
    private void redoRegisterEachInstance(String serviceName, String groupName, Instance instance) {
        try {
            clientProxy.registerService(serviceName, groupName, instance);
        } catch (NacosException e) {
            redoQueue.offer(new RedoRegisterTask(serviceName, groupName, instance));
            LogUtils.NAMING_LOGGER.warn(String
                    .format("redo register for service %s@@%s, %s failed, try again later.", groupName, serviceName, instance.toString()), e);
        }
    }
    
    private void startRedoFailedTaskThread() {
        redoExecutorService.submit(() -> {
            while (true) {
                try {
                    final Runnable task = redoQueue.take();
                    redoExecutorService.schedule(task, DEFAULT_REDO_DELAY, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    LogUtils.NAMING_LOGGER.warn("Take redo task interrupted", e);
                }
            }
        });
    }
    
    @Override
    public void onDisConnect() {
        connected = false;
        redoQueue.clear();
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
    
    class RedoSubscribeTask implements Runnable {
        
        String serviceName;
        
        String groupName;
        
        String clusters;
    
        public RedoSubscribeTask(String serviceName, String groupName, String clusters) {
            this.serviceName = serviceName;
            this.groupName = groupName;
            this.clusters = clusters;
        }
    
        @Override
        public void run() {
            try {
                clientProxy.subscribe(serviceName, groupName, clusters);
            } catch (NacosException e) {
                if (connected) {
                    redoQueue.offer(this);
                    LogUtils.NAMING_LOGGER.warn(String.format("re subscribe service %s failed, try again later.", serviceName), e);
                }
            }
        }
        
    }
    
    class RedoRegisterTask implements Runnable {
    
        String serviceName;
        
        String groupName;
        
        Instance instance;
    
        public RedoRegisterTask(String serviceName, String groupName, Instance instance) {
            this.serviceName = serviceName;
            this.groupName = groupName;
            this.instance = instance;
        }
    
        @Override
        public void run() {
            try {
                clientProxy.registerService(serviceName, groupName, instance);
            } catch (NacosException e) {
                if (connected) {
                    redoQueue.offer(this);
                    LogUtils.NAMING_LOGGER.warn(String
                            .format("redo register for service %s@@%s, %s failed, try again later.", groupName, serviceName, instance.toString()), e);
                }
            }
        }
    }
}
