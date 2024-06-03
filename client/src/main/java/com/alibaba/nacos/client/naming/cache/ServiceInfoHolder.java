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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.utils.CacheDirUtil;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Naming client service information holder.
 *
 * @author xiweng.yy
 */
public class ServiceInfoHolder implements Closeable {
    
    private final ConcurrentMap<String, ServiceInfo> serviceInfoMap;
    
    private final FailoverReactor failoverReactor;
    
    private final boolean pushEmptyProtection;
    
    private final InstancesDiffer instancesDiffer;
    
    private String cacheDir;
    
    private String notifierEventScope;
    
    public ServiceInfoHolder(String namespace, String notifierEventScope, NacosClientProperties properties) {
        cacheDir = CacheDirUtil.initCacheDir(namespace, properties);
        instancesDiffer = new InstancesDiffer();
        if (isLoadCacheAtStart(properties)) {
            this.serviceInfoMap = new ConcurrentHashMap<>(DiskCache.read(this.cacheDir));
        } else {
            this.serviceInfoMap = new ConcurrentHashMap<>(16);
        }
        this.failoverReactor = new FailoverReactor(this, notifierEventScope);
        this.pushEmptyProtection = isPushEmptyProtect(properties);
        this.notifierEventScope = notifierEventScope;
    }
    
    private boolean isLoadCacheAtStart(NacosClientProperties properties) {
        boolean loadCacheAtStart = false;
        if (properties != null && StringUtils.isNotEmpty(
                properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START))) {
            loadCacheAtStart = ConvertUtils.toBoolean(
                    properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START));
        }
        return loadCacheAtStart;
    }
    
    private boolean isPushEmptyProtect(NacosClientProperties properties) {
        boolean pushEmptyProtection = false;
        if (properties != null && StringUtils.isNotEmpty(
                properties.getProperty(PropertyKeyConst.NAMING_PUSH_EMPTY_PROTECTION))) {
            pushEmptyProtection = ConvertUtils.toBoolean(
                    properties.getProperty(PropertyKeyConst.NAMING_PUSH_EMPTY_PROTECTION));
        }
        return pushEmptyProtection;
    }
    
    public Map<String, ServiceInfo> getServiceInfoMap() {
        return serviceInfoMap;
    }
    
    public ServiceInfo getServiceInfo(final String serviceName, final String groupName, final String clusters) {
        String groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        String key = ServiceInfo.getKey(groupedServiceName, clusters);
        return serviceInfoMap.get(key);
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
        String serviceKey = serviceInfo.getKey();
        if (serviceKey == null) {
            NAMING_LOGGER.warn("process service info but serviceKey is null, service host: {}",
                    JacksonUtils.toJson(serviceInfo.getHosts()));
            return null;
        }
        ServiceInfo oldService = serviceInfoMap.get(serviceInfo.getKey());
        if (isEmptyOrErrorPush(serviceInfo)) {
            //empty or error push, just ignore
            NAMING_LOGGER.warn("process service info but found empty or error push, serviceKey: {}, "
                    + "pushEmptyProtection: {}, hosts: {}", serviceKey, pushEmptyProtection, serviceInfo.getHosts());
            return oldService;
        }
        serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);
        InstancesDiff diff = getServiceInfoDiff(oldService, serviceInfo);
        if (StringUtils.isBlank(serviceInfo.getJsonFromServer())) {
            serviceInfo.setJsonFromServer(JacksonUtils.toJson(serviceInfo));
        }
        MetricsMonitor.getServiceInfoMapSizeMonitor().set(serviceInfoMap.size());
        if (diff.hasDifferent()) {
            NAMING_LOGGER.info("current ips:({}) service: {} -> {}", serviceInfo.ipCount(), serviceInfo.getKey(),
                    JacksonUtils.toJson(serviceInfo.getHosts()));
            
            if (!failoverReactor.isFailoverSwitch(serviceKey)) {
                NotifyCenter.publishEvent(
                        new InstancesChangeEvent(notifierEventScope, serviceInfo.getName(), serviceInfo.getGroupName(),
                                serviceInfo.getClusters(), serviceInfo.getHosts(), diff));
            }
            DiskCache.write(serviceInfo, cacheDir);
        }
        return serviceInfo;
    }
    
    private boolean isEmptyOrErrorPush(ServiceInfo serviceInfo) {
        return null == serviceInfo.getHosts() || (pushEmptyProtection && !serviceInfo.validate());
    }
    
    private InstancesDiff getServiceInfoDiff(ServiceInfo oldService, ServiceInfo newService) {
        return instancesDiffer.doDiff(oldService, newService);
    }
    
    public String getCacheDir() {
        return cacheDir;
    }
    
    public boolean isFailoverSwitch() {
        return failoverReactor.isFailoverSwitch();
    }
    
    public ServiceInfo getFailoverServiceInfo(final String serviceName, final String groupName, final String clusters) {
        String groupedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        String key = ServiceInfo.getKey(groupedServiceName, clusters);
        return failoverReactor.getService(key);
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        failoverReactor.shutdown();
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
}
