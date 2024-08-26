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

package com.alibaba.nacos.console.handler.inner.auth;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.auth.RoleHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import org.springframework.stereotype.Service;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;

import java.util.List;

/**
 * Implementation of RoleHandler that handles role-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
public class RoleInnerHandler implements RoleHandler {
    
    private final NacosRoleServiceImpl roleService;
    
    public RoleInnerHandler(NacosRoleServiceImpl roleService) {
        this.roleService = roleService;
    }
    
    @Override
    public boolean createRole(String role, String username) {
        roleService.addRole(role, username);
        return true;
    }
    
    @Override
    public boolean deleteRole(String role, String username) {
        if (StringUtils.isBlank(username)) {
            roleService.deleteRole(role);
        } else {
            roleService.deleteRole(role, username);
        }
        return true;
    }
    
    @Override
    public Page<RoleInfo> getRoleList(int pageNo, int pageSize, String username, String role, String search) {
        if ("blur".equalsIgnoreCase(search)) {
            return roleService.findRolesLike4Page(username, role, pageNo, pageSize);
        } else {
            return roleService.getRolesFromDatabase(username, role, pageNo, pageSize);
        }
    }
    
    @Override
    public List<String> getRoleListByRoleName(String role) {
        return roleService.findRolesLikeRoleName(role);
    }
}
