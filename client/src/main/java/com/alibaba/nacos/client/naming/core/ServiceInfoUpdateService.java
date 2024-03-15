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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Service information update service.
 *
 * @author xiweng.yy
 */
public class ServiceInfoUpdateService implements Closeable {
    
    private static final long DEFAULT_DELAY = 1000L;
    
    private static final int DEFAULT_UPDATE_CACHE_TIME_MULTIPLE = 6;
    
    private static final int MIN_THREAD_NUM = 1;
    
    private final Map<String, ScheduledFuture<?>> futureMap = new HashMap<>();
    
    private final ServiceInfoHolder serviceInfoHolder;
    
    private final ScheduledExecutorService executor;
    
    private final NamingClientProxy namingClientProxy;
    
    private final InstancesChangeNotifier changeNotifier;
    
    private final boolean asyncQuerySubscribeService;
    
    public ServiceInfoUpdateService(NacosClientProperties properties, ServiceInfoHolder serviceInfoHolder,
            NamingClientProxy namingClientProxy, InstancesChangeNotifier changeNotifier) {
        this.asyncQuerySubscribeService = isAsyncQueryForSubscribeService(properties);
        this.executor = new ScheduledThreadPoolExecutor(initPollingThreadCount(properties),
                new NameThreadFactory("com.alibaba.nacos.client.naming.updater"));
        this.serviceInfoHolder = serviceInfoHolder;
        this.namingClientProxy = namingClientProxy;
        this.changeNotifier = changeNotifier;
    }
    
    private boolean isAsyncQueryForSubscribeService(NacosClientProperties properties) {
        if (properties == null || !properties.containsKey(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE)) {
            return false;
        }
        return ConvertUtils.toBoolean(properties.getProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE),
                false);
    }
    
    private int initPollingThreadCount(NacosClientProperties properties) {
        int count = ThreadUtils.getSuitableThreadCount(1) > 1 ? ThreadUtils.getSuitableThreadCount(1) / 2 : 1;
        if (properties == null) {
            return count;
        }
        count = Math.min(properties.getInteger(PropertyKeyConst.NAMING_POLLING_MAX_THREAD_COUNT, count), count);
        count = Math.max(count, MIN_THREAD_NUM);
        return properties.getInteger(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT, count);
    }
    
    /**
     * Schedule update if absent.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     */
    public void scheduleUpdateIfAbsent(String serviceName, String groupName, String clusters) {
        if (!asyncQuerySubscribeService) {
            return;
        }
        String serviceKey = ServiceInfo.getKey(NamingUtils.getGroupedName(serviceName, groupName), clusters);
        if (futureMap.get(serviceKey) != null) {
            return;
        }
        synchronized (futureMap) {
            if (futureMap.get(serviceKey) != null) {
                return;
            }
            
            ScheduledFuture<?> future = addTask(new UpdateTask(serviceName, groupName, clusters));
            futureMap.put(serviceKey, future);
        }
    }
    
    private synchronized ScheduledFuture<?> addTask(UpdateTask task) {
        return executor.schedule(task, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Stop to schedule update if contain task.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     */
    public void stopUpdateIfContain(String serviceName, String groupName, String clusters) {
        String serviceKey = ServiceInfo.getKey(NamingUtils.getGroupedName(serviceName, groupName), clusters);
        if (!futureMap.containsKey(serviceKey)) {
            return;
        }
        synchronized (futureMap) {
            if (!futureMap.containsKey(serviceKey)) {
                return;
            }
            futureMap.remove(serviceKey);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executor, NAMING_LOGGER);
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
    
    public class UpdateTask implements Runnable {
        
        long lastRefTime = Long.MAX_VALUE;
        
        private boolean isCancel;
        
        private final String serviceName;
        
        private final String groupName;
        
        private final String clusters;
        
        private final String groupedServiceName;
        
        private final String serviceKey;
        
        /**
         * the fail situation. 1:can't connect to server 2:serviceInfo's hosts is empty
         */
        private int failCount = 0;
        
        public UpdateTask(String serviceName, String groupName, String clusters) {
            this.serviceName = serviceName;
            this.groupName = groupName;
            this.clusters = clusters;
            this.groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
            this.serviceKey = ServiceInfo.getKey(groupedServiceName, clusters);
        }
        
        @Override
        public void run() {
            long delayTime = DEFAULT_DELAY;
            
            try {
                if (!changeNotifier.isSubscribed(groupName, serviceName, clusters) && !futureMap.containsKey(
                        serviceKey)) {
                    NAMING_LOGGER.info("update task is stopped, service:{}, clusters:{}", groupedServiceName, clusters);
                    isCancel = true;
                    return;
                }
                
                ServiceInfo serviceObj = serviceInfoHolder.getServiceInfoMap().get(serviceKey);
                if (serviceObj == null) {
                    serviceObj = namingClientProxy.queryInstancesOfService(serviceName, groupName, clusters, false);
                    serviceInfoHolder.processServiceInfo(serviceObj);
                    // TODO multiple time can be configured.
                    delayTime = serviceObj.getCacheMillis() * DEFAULT_UPDATE_CACHE_TIME_MULTIPLE;
                    lastRefTime = serviceObj.getLastRefTime();
                    return;
                }
                
                if (serviceObj.getLastRefTime() <= lastRefTime) {
                    serviceObj = namingClientProxy.queryInstancesOfService(serviceName, groupName, clusters, false);
                    serviceInfoHolder.processServiceInfo(serviceObj);
                }
                lastRefTime = serviceObj.getLastRefTime();
                if (CollectionUtils.isEmpty(serviceObj.getHosts())) {
                    incFailCount();
                    return;
                }
                // TODO multiple time can be configured.
                delayTime = serviceObj.getCacheMillis() * DEFAULT_UPDATE_CACHE_TIME_MULTIPLE;
                resetFailCount();
            } catch (NacosException e) {
                handleNacosException(e);
            } catch (Throwable e) {
                handleUnknownException(e);
            } finally {
                if (!isCancel) {
                    executor.schedule(this, Math.min(delayTime << failCount, DEFAULT_DELAY * 60),
                            TimeUnit.MILLISECONDS);
                }
            }
        }
        
        private void handleNacosException(NacosException e) {
            incFailCount();
            int errorCode = e.getErrCode();
            if (NacosException.SERVER_ERROR == errorCode) {
                handleUnknownException(e);
            }
            NAMING_LOGGER.warn("Can't update serviceName: {}, reason: {}", groupedServiceName, e.getErrMsg());
        }
        
        private void handleUnknownException(Throwable throwable) {
            incFailCount();
            NAMING_LOGGER.warn("[NA] failed to update serviceName: {}", groupedServiceName, throwable);
        }
        
        private void incFailCount() {
            int limit = 6;
            if (failCount == limit) {
                return;
            }
            failCount++;
        }
        
        private void resetFailCount() {
            failCount = 0;
        }
    }
}
