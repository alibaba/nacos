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
import com.alibaba.nacos.console.handler.auth.RoleHandler;
import com.alibaba.nacos.console.handler.inner.auth.RoleInnerHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy class for handling role-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
public class RoleProxy {
    
    private final Map<String, RoleHandler> roleHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new RoleProxy with the given RoleInnerHandler and ConsoleConfig.
     *
     * @param roleInnerHandler the default implementation of RoleHandler
     * @param consoleConfig    the console configuration used to determine the deployment type
     */
    public RoleProxy(RoleInnerHandler roleInnerHandler, ConsoleConfig consoleConfig) {
        this.roleHandlerMap.put("merged", roleInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Adds a role to a user or creates a role and binds it to GLOBAL_ADMIN.
     *
     * @param role     the role name
     * @param username the username
     * @return true if the operation was successful
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public boolean createRole(String role, String username) {
        RoleHandler roleHandler = roleHandlerMap.get(consoleConfig.getType());
        if (roleHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return roleHandler.createRole(role, username);
    }
    
    /**
     * Deletes a role or deletes all users under this role if no username is specified.
     *
     * @param role     the role name
     * @param username the username (optional)
     * @return true if the operation was successful
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public boolean deleteRole(String role, String username) {
        RoleHandler roleHandler = roleHandlerMap.get(consoleConfig.getType());
        if (roleHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return roleHandler.deleteRole(role, username);
    }
    
    /**
     * Retrieves a paginated list of roles with the option for accurate or fuzzy search.
     *
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param username the username (optional)
     * @param role     the role name (optional)
     * @param search   the type of search: "accurate" or "blur"
     * @return a paginated list of roles
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public Page<RoleInfo> getRoleList(int pageNo, int pageSize, String username, String role, String search) {
        RoleHandler roleHandler = roleHandlerMap.get(consoleConfig.getType());
        if (roleHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return roleHandler.getRoleList(pageNo, pageSize, username, role, search);
    }
    
    /**
     * Fuzzy matches a role name.
     *
     * @param role the role name
     * @return a list of matching roles
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public List<String> getRoleListByRoleName(String role) {
        RoleHandler roleHandler = roleHandlerMap.get(consoleConfig.getType());
        if (roleHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return roleHandler.getRoleListByRoleName(role);
    }
}

