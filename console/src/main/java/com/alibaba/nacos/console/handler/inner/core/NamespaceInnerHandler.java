/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler.inner.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.core.NamespaceHandler;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of NamespaceHandler that handles namespace-related operations.
 *
 * @author zhangyukun
 */
@Service
public class NamespaceInnerHandler implements NamespaceHandler {
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final NamespacePersistService namespacePersistService;
    
    public NamespaceInnerHandler(NamespaceOperationService namespaceOperationService,
            NamespacePersistService namespacePersistService) {
        this.namespaceOperationService = namespaceOperationService;
        this.namespacePersistService = namespacePersistService;
    }
    
    @Override
    public List<Namespace> getNamespaceList() {
        return namespaceOperationService.getNamespaceList();
    }
    
    @Override
    public Namespace getNamespaceDetail(String namespaceId) throws NacosException {
        return namespaceOperationService.getNamespace(namespaceId);
    }
    
    @Override
    public Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc)
            throws NacosException {
        return namespaceOperationService.createNamespace(namespaceId, namespaceName, namespaceDesc);
    }
    
    @Override
    public Boolean updateNamespace(NamespaceForm namespaceForm) throws NacosException {
        return namespaceOperationService.editNamespace(namespaceForm.getNamespaceId(), namespaceForm.getNamespaceName(),
                namespaceForm.getNamespaceDesc());
    }
    
    @Override
    public Boolean deleteNamespace(String namespaceId) {
        return namespaceOperationService.removeNamespace(namespaceId);
    }
    
    @Override
    public Boolean checkNamespaceIdExist(String namespaceId) {
        return (namespacePersistService.tenantInfoCountByTenantId(namespaceId) > 0);
    }
}

