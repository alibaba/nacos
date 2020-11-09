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

package com.alibaba.nacos.console.service;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * namespace service.
 *
 * @author horizonzy
 * @since 1.4.0
 */
@Service
public class NamespaceServiceImpl {
    
    @Autowired
    private PersistService persistService;
    
    private List<String> namespaceIds = new ArrayList<>();
    
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    private void reload() {
        try {
            List<String> namespaceIds = new ArrayList<>(this.namespaceIds.size());
            namespaceIds.add(Constants.DEFAULT_NAMESPACE_ID);
            List<TenantInfo> tenantInfos = persistService.findTenantByKp("1");
            for (TenantInfo tenantInfo : tenantInfos) {
                namespaceIds.add(tenantInfo.getTenantId());
            }
            this.namespaceIds = namespaceIds;
        } catch (Exception e) {
            Loggers.CORE.warn("[LOAD-TENANT-IDS] load failed", e);
        }
    }
    
    public void addNamespaceId(String namespaceId) {
        namespaceIds.add(namespaceId);
    }
    
    public void deleteNamespaceId(String namespaceId) {
        namespaceIds.remove(namespaceId);
    }
    
    /**
     * check namespaceId is exist.
     *
     * @param namespaceId namespaceId
     * @return is namespaceId is exist
     */
    public boolean namespaceIsExist(String namespaceId) {
        return namespaceIds.contains(namespaceId);
    }
}
