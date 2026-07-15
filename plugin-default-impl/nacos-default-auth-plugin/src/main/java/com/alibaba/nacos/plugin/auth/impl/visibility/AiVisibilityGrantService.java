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
 * Service for plugin-owned AI visibility grants.
 *
 * @author Zhengcy05
 */
public interface AiVisibilityGrantService {
    
    /**
     * Grant AI visibility access to one user.
     */
    void grant(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException;
    
    /**
     * Revoke AI visibility access from one user.
     */
    void revoke(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException;
    
    /**
     * List current grants for one AI resource.
     */
    List<AiVisibilityGrantInfo> list(String namespaceId, String resourceType,
        String resourceName) throws NacosException;
    
    /**
     * Find explicitly authorized resource names for one user and query action.
     */
    List<String> findAuthorizedResourceNames(String username, String namespaceId,
        String resourceType, String action);
}
