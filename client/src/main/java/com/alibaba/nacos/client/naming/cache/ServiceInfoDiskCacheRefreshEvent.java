/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

/**
 * Event for async service info disk cache refresh.
 *
 * @author Zhengcy05
 */
public class ServiceInfoDiskCacheRefreshEvent {
    
    private final String serviceKey;
    
    private final ServiceInfo serviceInfo;
    
    private final String cacheDir;
    
    /**
     * Create a disk cache refresh event.
     *
     * @param serviceKey service key without clusters
     * @param serviceInfo latest service info snapshot
     * @param cacheDir disk cache directory
     */
    public ServiceInfoDiskCacheRefreshEvent(String serviceKey, ServiceInfo serviceInfo,
        String cacheDir) {
        this.serviceKey = serviceKey;
        this.serviceInfo = serviceInfo;
        this.cacheDir = cacheDir;
    }
    
    /**
     * Get service key.
     *
     * @return service key
     */
    public String getServiceKey() {
        return serviceKey;
    }
    
    /**
     * Get service info snapshot.
     *
     * @return service info snapshot
     */
    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }
    
    /**
     * Get disk cache directory.
     *
     * @return disk cache directory
     */
    public String getCacheDir() {
        return cacheDir;
    }
}
