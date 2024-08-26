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

package com.alibaba.nacos.console.proxy.auth;

import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.auth.PermissionHandler;
import com.alibaba.nacos.console.handler.inner.auth.PermissionInnerHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy class for handling permission-related operations.
 *
 * @author zhangyukun
 */
@Service
public class PermissionProxy {
    
    private final Map<String, PermissionHandler> permissionHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new PermissionProxy with the given PermissionInnerHandler and ConsoleConfig.
     *
     * @param permissionInnerHandler the default implementation of PermissionHandler
     * @param consoleConfig          the console configuration used to determine the deployment type
     */
    public PermissionProxy(PermissionInnerHandler permissionInnerHandler, ConsoleConfig consoleConfig) {
        this.permissionHandlerMap.put("merged", permissionInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Adds a permission to a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return true if the operation was successful
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public boolean createPermission(String role, String resource, String action) {
        PermissionHandler permissionHandler = permissionHandlerMap.get(consoleConfig.getType());
        if (permissionHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return permissionHandler.createPermission(role, resource, action);
    }
    
    /**
     * Deletes a permission from a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return true if the operation was successful
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public boolean deletePermission(String role, String resource, String action) {
        PermissionHandler permissionHandler = permissionHandlerMap.get(consoleConfig.getType());
        if (permissionHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return permissionHandler.deletePermission(role, resource, action);
    }
    
    /**
     * Retrieves a paginated list of permissions for a role with the option for accurate or fuzzy search.
     *
     * @param role     the role
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param search   the type of search: "accurate" or "blur"
     * @return a paginated list of permissions
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public Page<PermissionInfo> getPermissionList(String role, int pageNo, int pageSize, String search) {
        PermissionHandler permissionHandler = permissionHandlerMap.get(consoleConfig.getType());
        if (permissionHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return permissionHandler.getPermissionList(role, pageNo, pageSize, search);
    }
}
