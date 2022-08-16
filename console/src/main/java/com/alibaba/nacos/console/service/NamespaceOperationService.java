/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.console.enums.NamespaceTypeEnum;
import com.alibaba.nacos.console.model.Namespace;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * NamespaceOperationService.
 * @author dongyafei
 * @date 2022/8/16
 */

@Service
public class NamespaceOperationService {
    
    private final PersistService persistService;
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final int DEFAULT_QUOTA = 200;
    
    private static final String DEFAULT_CREATE_SOURCE = "nacos";
    
    private static final String DEFAULT_TENANT = "";
    
    private static final String DEFAULT_KP = "1";
    
    public NamespaceOperationService(PersistService persistService) {
        this.persistService = persistService;
    }
    
    public List<Namespace> getNamespaceList() {
        // TODO 获取用kp
        List<TenantInfo> tenantInfos = persistService.findTenantByKp(DEFAULT_KP);
        
        Namespace namespace0 = new Namespace("", DEFAULT_NAMESPACE, DEFAULT_QUOTA,
                persistService.configInfoCount(DEFAULT_TENANT), NamespaceTypeEnum.GLOBAL.getType());
        List<Namespace> namespaceList = new ArrayList<>();
        namespaceList.add(namespace0);
        
        for (TenantInfo tenantInfo : tenantInfos) {
            int configCount = persistService.configInfoCount(tenantInfo.getTenantId());
            Namespace namespaceTmp = new Namespace(tenantInfo.getTenantId(), tenantInfo.getTenantName(),
                    tenantInfo.getTenantDesc(), DEFAULT_QUOTA, configCount, NamespaceTypeEnum.CUSTOM.getType());
            namespaceList.add(namespaceTmp);
        }
        return namespaceList;
    }
    
    /**
     * create namespace.
     *
     * @param namespaceId   namespace ID
     * @param namespaceName namespace Name
     * @param namespaceDesc namespace Desc
     * @param isV2          whether api v2
     * @return whether create ok
     */
    public Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc, Boolean isV2)
            throws NacosException {
        // TODO 获取用kp
        if (persistService.tenantInfoCountByTenantId(namespaceId) > 0) {
            if (isV2) {
                throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.NAMESPACE_ALREADY_EXIST,
                        "namespaceId [" + namespaceId + "] already exist");
            } else {
                return false;
            }
        }
        
        persistService
                .insertTenantInfoAtomic(DEFAULT_KP, namespaceId, namespaceName, namespaceDesc, DEFAULT_CREATE_SOURCE,
                        System.currentTimeMillis());
        return true;
    }
    
    /**
     * edit namespace.
     */
    public Boolean editNamespace(String namespaceId, String namespaceName, String namespaceDesc) {
        // TODO 获取用kp
        persistService.updateTenantNameAtomic(DEFAULT_KP, namespaceId, namespaceName, namespaceDesc);
        return true;
    }
    
    /**
     * remove namespace.
     */
    public Boolean removeNamespace(String namespaceId) {
        persistService.removeTenantInfoAtomic(DEFAULT_KP, namespaceId);
        return true;
    }
}
