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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.core.NamespaceHandler;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Proxy class for handling namespace operations.
 *
 * @author zhangyukun
 */
@Service
public class NamespaceProxy {
    
    private final NamespaceHandler namespaceHandler;
    
    public NamespaceProxy(NamespaceHandler namespaceHandler) {
        this.namespaceHandler = namespaceHandler;
    }
    
    /**
     * Get namespace list.
     */
    public List<Namespace> getNamespaceList() throws NacosException {
        return namespaceHandler.getNamespaceList();
    }
    
    /**
     * Get the specific namespace information.
     */
    public Namespace getNamespaceDetail(String namespaceId) throws NacosException {
        return namespaceHandler.getNamespaceDetail(namespaceId);
    }
    
    /**
     * Create or update namespace.
     */
    public Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc)
            throws NacosException {
        return namespaceHandler.createNamespace(namespaceId, namespaceName, namespaceDesc);
    }
    
    /**
     * Edit namespace.
     */
    public Boolean updateNamespace(NamespaceForm namespaceForm) throws NacosException {
        return namespaceHandler.updateNamespace(namespaceForm);
    }
    
    /**
     * Delete namespace.
     */
    public Boolean deleteNamespace(String namespaceId) throws NacosException {
        return namespaceHandler.deleteNamespace(namespaceId);
    }
    
    /**
     * Check if namespace exists.
     */
    public Boolean checkNamespaceIdExist(String namespaceId) throws NacosException {
        return namespaceHandler.checkNamespaceIdExist(namespaceId);
    }
}
