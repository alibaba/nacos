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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import com.alibaba.nacos.client.naming.cache.DiskCache;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Host reactor.
 *
 * @author xuanyin
 */
public class HostReactor implements Closeable {
    
    private static final long DEFAULT_DELAY = 1000L;
    
    private static final long UPDATE_HOLD_INTERVAL = 5000L;
    
    private final Map<String, ScheduledFuture<?>> futureMap = new HashMap<String, ScheduledFuture<?>>();
    
    private final Map<String, ServiceInfo> serviceInfoMap;
    
    private final Map<String, Object> updatingMap;
    
    private final PushReceiver pushReceiver;
    
    private final EventDispatcher eventDispatcher;
    
    private final NamingHttpClientProxy serverProxy;
    
    private final FailoverReactor failoverReactor;
    
    private final ScheduledExecutorService executor;
    
    private String cacheDir;
    
    public HostReactor(EventDispatcher eventDispatcher, NamingHttpClientProxy serverProxy, String namespace,
            Properties properties) {
        initCacheDir(namespace);
        // init executorService
        this.executor = new ScheduledThreadPoolExecutor(initPollingThreadCount(properties), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("com.alibaba.nacos.client.naming.updater");
                return thread;
            }
        });
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = serverProxy;
        if (isLoadCacheAtStart(properties)) {
            this.serviceInfoMap = new ConcurrentHashMap<String, ServiceInfo>(DiskCache.read(this.cacheDir));
        } else {
            this.serviceInfoMap = new ConcurrentHashMap<String, ServiceInfo>(16);
        }
        
        this.updatingMap = new ConcurrentHashMap<String, Object>();
        this.failoverReactor = new FailoverReactor(this, cacheDir);
        this.pushReceiver = new PushReceiver(this);
    }
    
    private void initCacheDir(String namespace) {
        cacheDir = System.getProperty("com.alibaba.nacos.naming.cache.dir");
        if (StringUtils.isEmpty(cacheDir)) {
            cacheDir = System.getProperty("user.home") + "/nacos/naming/" + namespace;
        }
    }
    
    private int initPollingThreadCount(Properties properties) {
        if (properties == null) {
            return UtilAndComs.DEFAULT_POLLING_THREAD_COUNT;
        }
        return ConvertUtils.toInt(properties.getProperty(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT),
                UtilAndComs.DEFAULT_POLLING_THREAD_COUNT);
    }
    
    private boolean isLoadCacheAtStart(Properties properties) {
        boolean loadCacheAtStart = false;
        if (properties != null && StringUtils
                .isNotEmpty(properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START))) {
            loadCacheAtStart = ConvertUtils
                    .toBoolean(properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START));
        }
        return loadCacheAtStart;
    }
    
    public Map<String, ServiceInfo> getServiceInfoMap() {
        return serviceInfoMap;
    }
    
    public synchronized ScheduledFuture<?> addTask(UpdateTask task) {
        return executor.schedule(task, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Process service json.
     *
     * @param json service json
     * @return service info
     */
    public ServiceInfo processServiceInfo(String json) {
        ServiceInfo serviceInfo = JacksonUtils.toObj(json, ServiceInfo.class);
        serviceInfo.setJsonFromServer(json);
        return processServiceInfo(serviceInfo);
    }
    
    /**
     * Process service info.
     *
     * @param serviceInfo new service info
     * @return service info
     */
    public ServiceInfo processServiceInfo(ServiceInfo serviceInfo) {
        ServiceInfo oldService = serviceInfoMap.get(serviceInfo.getKey());
        if (serviceInfo.getHosts() == null || !serviceInfo.validate()) {
            //empty or error push, just ignore
            return oldService;
        }
        
        boolean changed = false;
        
        if (oldService != null) {
            
            if (oldService.getLastRefTime() > serviceInfo.getLastRefTime()) {
                NAMING_LOGGER.warn("out of date data received, old-t: " + oldService.getLastRefTime() + ", new-t: "
                        + serviceInfo.getLastRefTime());
            }
            
            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);
            
            Map<String, Instance> oldHostMap = new HashMap<String, Instance>(oldService.getHosts().size());
            for (Instance host : oldService.getHosts()) {
                oldHostMap.put(host.toInetAddr(), host);
            }
            
            Map<String, Instance> newHostMap = new HashMap<String, Instance>(serviceInfo.getHosts().size());
            for (Instance host : serviceInfo.getHosts()) {
                newHostMap.put(host.toInetAddr(), host);
            }
            
            Set<Instance> modHosts = new HashSet<Instance>();
            Set<Instance> newHosts = new HashSet<Instance>();
            Set<Instance> remvHosts = new HashSet<Instance>();
            
            List<Map.Entry<String, Instance>> newServiceHosts = new ArrayList<Map.Entry<String, Instance>>(
                    newHostMap.entrySet());
            for (Map.Entry<String, Instance> entry : newServiceHosts) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                if (oldHostMap.containsKey(key) && !StringUtils
                        .equals(host.toString(), oldHostMap.get(key).toString())) {
                    modHosts.add(host);
                    continue;
                }
                
                if (!oldHostMap.containsKey(key)) {
                    newHosts.add(host);
                }
            }
            
            for (Map.Entry<String, Instance> entry : oldHostMap.entrySet()) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                if (newHostMap.containsKey(key)) {
                    continue;
                }
                
                if (!newHostMap.containsKey(key)) {
                    remvHosts.add(host);
                }
                
            }
            
            if (newHosts.size() > 0) {
                changed = true;
                NAMING_LOGGER.info("new ips(" + newHosts.size() + ") service: " + serviceInfo.getKey() + " -> "
                        + JacksonUtils.toJson(newHosts));
            }
            
            if (remvHosts.size() > 0) {
                changed = true;
                NAMING_LOGGER.info("removed ips(" + remvHosts.size() + ") service: " + serviceInfo.getKey() + " -> "
                        + JacksonUtils.toJson(remvHosts));
            }
            
            if (modHosts.size() > 0) {
                changed = true;
                serverProxy.updateBeatInfo(modHosts);
                NAMING_LOGGER.info("modified ips(" + modHosts.size() + ") service: " + serviceInfo.getKey() + " -> "
                        + JacksonUtils.toJson(modHosts));
            }
            
            if (newHosts.size() > 0 || remvHosts.size() > 0 || modHosts.size() > 0) {
                eventDispatcher.serviceChanged(serviceInfo);
                DiskCache.write(serviceInfo, cacheDir);
            }
            
        } else {
            changed = true;
            NAMING_LOGGER.info("init new ips(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getKey() + " -> "
                    + JacksonUtils.toJson(serviceInfo.getHosts()));
            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);
            eventDispatcher.serviceChanged(serviceInfo);
            DiskCache.write(serviceInfo, cacheDir);
        }
        
        if (StringUtils.isBlank(serviceInfo.getJsonFromServer())) {
            serviceInfo.setJsonFromServer(JacksonUtils.toJson(serviceInfo));
        }
        
        MetricsMonitor.getServiceInfoMapSizeMonitor().set(serviceInfoMap.size());
        
        if (changed) {
            NAMING_LOGGER.info("current ips:(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getKey() + " -> "
                    + JacksonUtils.toJson(serviceInfo.getHosts()));
        }
        
        return serviceInfo;
    }
    
    private ServiceInfo getServiceInfo0(String groupedServiceName, String clusters) {
        
        String key = ServiceInfo.getKey(groupedServiceName, clusters);
        
        return serviceInfoMap.get(key);
    }
    
    public ServiceInfo getServiceInfoDirectlyFromServer(final String serviceName, final String groupName,
            final String clusters) throws NacosException {
        return serverProxy.queryInstancesOfService(serviceName, groupName, clusters, 0, false);
    }
    
    public ServiceInfo getServiceInfo(final String serviceName, final String groupName, final String clusters) {
        
        NAMING_LOGGER.debug("failover-mode: " + failoverReactor.isFailoverSwitch());
        String groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        String key = ServiceInfo.getKey(groupedServiceName, clusters);
        if (failoverReactor.isFailoverSwitch()) {
            return failoverReactor.getService(key);
        }
        
        ServiceInfo serviceObj = getServiceInfo0(groupedServiceName, clusters);
        
        if (null == serviceObj) {
            serviceObj = new ServiceInfo(groupedServiceName, clusters);
            
            serviceInfoMap.put(serviceObj.getKey(), serviceObj);
            
            updatingService(groupedServiceName);
            updateServiceNow(serviceName, groupName, clusters);
            finishUpdating(groupedServiceName);
            
        } else if (updatingMap.containsKey(groupedServiceName)) {
            
            if (UPDATE_HOLD_INTERVAL > 0) {
                // hold a moment waiting for update finish
                synchronized (serviceObj) {
                    try {
                        serviceObj.wait(UPDATE_HOLD_INTERVAL);
                    } catch (InterruptedException e) {
                        NAMING_LOGGER
                                .error("[getServiceInfo] serviceName:" + serviceName + ", clusters:" + clusters, e);
                    }
                }
            }
        }
        
        scheduleUpdateIfAbsent(serviceName, groupName, clusters);
        
        return serviceInfoMap.get(serviceObj.getKey());
    }
    
    /**
     * Schedule update if absent.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     */
    public void scheduleUpdateIfAbsent(String serviceName, String groupName, String clusters) {
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
    
    /**
     * Update service now.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     */
    public void updateServiceNow(String serviceName, String groupName, String clusters) {
        ServiceInfo oldService = getServiceInfo0(serviceName, clusters);
        try {
            ServiceInfo result = serverProxy
                    .queryInstancesOfService(serviceName, groupName, clusters, pushReceiver.getUdpPort(), false);
            if (null != result) {
                processServiceInfo(result);
            }
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] failed to update serviceName: " + serviceName, e);
        } finally {
            if (oldService != null) {
                synchronized (oldService) {
                    oldService.notifyAll();
                }
            }
        }
    }
    
    /**
     * Refresh only.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    cluster
     */
    public void refreshOnly(String serviceName, String groupName, String clusters) {
        try {
            serverProxy.queryInstancesOfService(serviceName, groupName, clusters, pushReceiver.getUdpPort(), false);
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] failed to update serviceName: " + serviceName, e);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executor, NAMING_LOGGER);
        pushReceiver.shutdown();
        failoverReactor.shutdown();
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
    
    public void updatingService(String serviceName) {
        updatingMap.put(serviceName, new Object());
    }
    
    public void finishUpdating(String serviceName) {
        updatingMap.remove(serviceName);
    }
    
    public class UpdateTask implements Runnable {
        
        long lastRefTime = Long.MAX_VALUE;
        
        private final String serviceName;
        
        private final String groupName;
        
        private final String clusters;
        
        private final String groupedServiceName;
        
        private final String serviceKey;
        
        public UpdateTask(String serviceName, String groupName, String clusters) {
            this.serviceName = serviceName;
            this.groupName = groupName;
            this.clusters = clusters;
            this.groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
            this.serviceKey = ServiceInfo.getKey(groupedServiceName, clusters);
        }
        
        @Override
        public void run() {
            long delayTime = -1;
            
            try {
                ServiceInfo serviceObj = serviceInfoMap.get(serviceKey);
                
                if (serviceObj == null) {
                    updateServiceNow(serviceName, groupName, clusters);
                    delayTime = DEFAULT_DELAY;
                    return;
                }
                
                if (serviceObj.getLastRefTime() <= lastRefTime) {
                    updateServiceNow(serviceName, groupName, clusters);
                    serviceObj = serviceInfoMap.get(serviceKey);
                } else {
                    // if serviceName already updated by push, we should not override it
                    // since the push data may be different from pull through force push
                    refreshOnly(serviceName, groupName, clusters);
                }
                
                lastRefTime = serviceObj.getLastRefTime();
                
                if (!eventDispatcher.isSubscribed(groupedServiceName, clusters) && !futureMap.containsKey(serviceKey)) {
                    // abort the update task
                    NAMING_LOGGER
                            .info("update task is stopped, service:" + groupedServiceName + ", clusters:" + clusters);
                    return;
                }
                
                delayTime = serviceObj.getCacheMillis();
                
            } catch (Throwable e) {
                NAMING_LOGGER.warn("[NA] failed to update serviceName: " + groupedServiceName, e);
            } finally {
                if (delayTime > 0) {
                    executor.schedule(this, delayTime, TimeUnit.MILLISECONDS);
                }
            }
            
        }
    }
}
