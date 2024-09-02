/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.capacity.TenantCapacityPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.namespace.injector.AbstractNamespaceDetailInjector;
import com.alibaba.nacos.core.namespace.model.Namespace;
import org.springframework.stereotype.Service;

/**
 * Namespace detail for config info.
 *
 * @author xiweng.yy
 */
@Service
public class NamespaceConfigInfoService extends AbstractNamespaceDetailInjector {
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final TenantCapacityPersistService tenantCapacityPersistService;
    
    public NamespaceConfigInfoService(ConfigInfoPersistService configInfoPersistService,
            TenantCapacityPersistService tenantCapacityPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.tenantCapacityPersistService = tenantCapacityPersistService;
    }
    
    @Override
    public void injectDetail(Namespace namespace) {
        // set tenant quota
        TenantCapacity tenantCapacity = tenantCapacityPersistService.getTenantCapacity(namespace.getNamespace());
        if (tenantCapacity != null && tenantCapacity.getQuota() != null && tenantCapacity.getQuota() > 0) {
            namespace.setQuota(tenantCapacity.getQuota());
        } else {
            namespace.setQuota(PropertyUtil.getDefaultTenantQuota());
        }
        // set config count.
        int configCount = configInfoPersistService.configInfoCount(namespace.getNamespace());
        namespace.setConfigCount(configCount);
    }
}
