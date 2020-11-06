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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;

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
    
    private List<String> tenantIds = new ArrayList<>();
    
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    private void reload() {
        try {
            List<String> tmpTenantIds = new ArrayList<>(tenantIds.size());
            List<TenantInfo> tenantInfos = persistService.findTenantByKp("1");
            for (TenantInfo tenantInfo : tenantInfos) {
                tmpTenantIds.add(tenantInfo.getTenantId());
            }
            tenantIds = tmpTenantIds;
        } catch (Exception e) {
            Loggers.CORE.warn("[LOAD-TENANT-IDS] load failed", e);
        }
    }
    
    public void addTenantId(String tenantId) {
        tenantIds.add(tenantId);
    }
    
    public void deleteTenantId(String tenantId) {
        tenantIds.remove(tenantId);
    }
    
    /**
     * check tenantId is exist.
     *
     * @param tenantId tenantId
     * @return is tenant is exist
     */
    public boolean tenantIsExist(String tenantId) {
        if (StringUtils.isEmpty(tenantId) || DEFAULT_NAMESPACE_ID.equals(tenantId)) {
            return true;
        }
        return tenantIds.contains(tenantId);
    }
}
