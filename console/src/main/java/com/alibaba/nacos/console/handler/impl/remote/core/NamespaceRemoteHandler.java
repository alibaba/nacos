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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.core.NamespaceHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Remote Implementation of NamespaceHandler that handles namespace-related operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class NamespaceRemoteHandler implements NamespaceHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public NamespaceRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public List<Namespace> getNamespaceList() throws NacosException {
        return transferToNamespaceList(clientHolder.getNamingMaintainerService().getNamespaceList());
    }
    
    private List<Namespace> transferToNamespaceList(List<com.alibaba.nacos.api.model.response.Namespace> namespaceList) {
        if (null == namespaceList) {
            return Collections.emptyList();
        }
        List<Namespace> result = new ArrayList<>();
        namespaceList.forEach(namespace -> result.add(transferToNamespace(namespace)));
        return result;
    }
    
    @Override
    public Namespace getNamespaceDetail(String namespaceId) throws NacosException {
        return transferToNamespace(clientHolder.getNamingMaintainerService().getNamespace(namespaceId));
    }
    
    private Namespace transferToNamespace(com.alibaba.nacos.api.model.response.Namespace namespace) {
        Namespace targetNamespace = new Namespace();
        targetNamespace.setNamespaceShowName(namespace.getNamespaceShowName());
        targetNamespace.setNamespaceDesc(namespace.getNamespaceDesc());
        targetNamespace.setNamespace(namespace.getNamespace());
        targetNamespace.setQuota(namespace.getQuota());
        targetNamespace.setConfigCount(namespace.getConfigCount());
        targetNamespace.setType(namespace.getType());
        return targetNamespace;
    }
    
    @Override
    public Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc)
            throws NacosException {
        return clientHolder.getNamingMaintainerService().createNamespace(namespaceId, namespaceName, namespaceDesc);
    }
    
    @Override
    public Boolean updateNamespace(NamespaceForm namespaceForm) throws NacosException {
        return clientHolder.getNamingMaintainerService().updateNamespace(namespaceForm.getNamespaceId(),
                namespaceForm.getNamespaceName(), namespaceForm.getNamespaceDesc());
    }
    
    @Override
    public Boolean deleteNamespace(String namespaceId) throws NacosException {
        return clientHolder.getNamingMaintainerService().deleteNamespace(namespaceId);
    }
    
    @Override
    public Boolean checkNamespaceIdExist(String namespaceId) throws NacosException {
        return clientHolder.getNamingMaintainerService().checkNamespaceIdExist(namespaceId);
    }
}

