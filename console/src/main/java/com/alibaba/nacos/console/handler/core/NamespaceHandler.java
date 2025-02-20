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

package com.alibaba.nacos.console.handler.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;

import java.util.List;

/**
 * Interface for handling namespace-related operations.
 *
 * @author zhangyukun
 */
public interface NamespaceHandler {
    
    /**
     * Get a list of namespaces.
     *
     * @return list of namespaces
     * @throws NacosException if there is an issue fetching the namespaces
     */
    List<Namespace> getNamespaceList() throws NacosException;
    
    /**
     * Get details of a specific namespace.
     *
     * @param namespaceId the ID of the namespace
     * @return namespace details
     * @throws NacosException if there is an issue fetching the namespace
     */
    Namespace getNamespaceDetail(String namespaceId) throws NacosException;
    
    /**
     * Create a new namespace.
     *
     * @param namespaceId   the ID of the namespace
     * @param namespaceName the name of the namespace
     * @param namespaceDesc the description of the namespace
     * @return true if the namespace was successfully created, otherwise false
     * @throws NacosException if there is an issue creating the namespace
     */
    Boolean createNamespace(String namespaceId, String namespaceName, String namespaceDesc) throws NacosException;
    
    /**
     * Update an existing namespace.
     *
     * @param namespaceForm the form containing the updated namespace details
     * @return true if the namespace was successfully updated, otherwise false
     * @throws NacosException if there is an issue updating the namespace
     */
    Boolean updateNamespace(NamespaceForm namespaceForm) throws NacosException;
    
    /**
     * Delete a namespace by its ID.
     *
     * @param namespaceId the ID of the namespace
     * @return true if the namespace was successfully deleted, otherwise false
     * @throws NacosException if there is an issue deleting the namespace
     */
    Boolean deleteNamespace(String namespaceId) throws NacosException;
    
    /**
     * Check if a namespace ID exists.
     *
     * @param namespaceId the ID of the namespace to check
     * @return true if the namespace exists, otherwise false
     * @throws NacosException if there is an issue checking the namespace
     */
    Boolean checkNamespaceIdExist(String namespaceId) throws NacosException;
}

