/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.auth.AuthService;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NacosAuthService.
 *
 * @author : huangtianhui
 */
@Service
public class NacosAuthServiceImpl implements AuthService {
    
    private final AuthConfigs authConfigs;
    
    private final RolePersistService rolePersistService;
    
    public NacosAuthServiceImpl(AuthConfigs authConfigs, RolePersistService rolePersistService) {
        this.authConfigs = authConfigs;
        this.rolePersistService = rolePersistService;
    }
    
    /**
     * check if all user has at least one admin role.
     *
     * @return true if all user has at least one admin role.
     */
    @Override
    public boolean hasGlobalAdminRole() {
        if (authConfigs.isHasGlobalAdminRole()) {
            return true;
        }
        List<RoleInfo> roles = getAllRoles();
        boolean hasGlobalAdminRole = CollectionUtils.isNotEmpty(roles) && roles.stream()
                .anyMatch(roleInfo -> AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole()));
        authConfigs.setHasGlobalAdminRole(hasGlobalAdminRole);
        return hasGlobalAdminRole;
    }
    
    private List<RoleInfo> getAllRoles() {
        Page<RoleInfo> roleInfoPage = rolePersistService.getRolesByUserNameAndRoleName(StringUtils.EMPTY,
                StringUtils.EMPTY, 1, Integer.MAX_VALUE);
        if (roleInfoPage == null) {
            return null;
        }
        return roleInfoPage.getPageItems();
    }
}
