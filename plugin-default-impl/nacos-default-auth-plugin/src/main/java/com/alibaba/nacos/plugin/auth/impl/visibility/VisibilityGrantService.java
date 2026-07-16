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

package com.alibaba.nacos.plugin.auth.impl.visibility;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.List;

/**
 * Service for plugin-owned visibility grants.
 *
 * @author Zhengcy05
 */
public interface VisibilityGrantService {
    
    /**
     * Grant visibility access to one user.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @param username grantee username
     * @param action requested grant action, {@code r}, {@code w}, or {@code rw}
     * @throws NacosException if the resource or grantee is invalid, or the caller cannot manage grants
     */
    void grant(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException;
    
    /**
     * Revoke visibility access from one user.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @param username grantee username
     * @param action grant action to revoke, {@code r}, {@code w}, or {@code rw}
     * @throws NacosException if the resource or action is invalid, or the caller cannot manage grants
     */
    void revoke(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException;
    
    /**
     * List current grants for one resource.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @return grants currently attached to the resource
     * @throws NacosException if the resource is invalid or the caller cannot manage grants
     */
    List<VisibilityGrantInfo> list(String namespaceId, String resourceType,
        String resourceName) throws NacosException;
    
    /**
     * Find explicitly authorized resource names for one user and query action.
     *
     * @param username grantee username
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param action query action
     * @return resource names explicitly authorized for the user
     */
    List<String> findAuthorizedResourceNames(String username, String namespaceId,
        String resourceType, String action);
}
