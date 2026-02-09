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

package com.alibaba.nacos.legacy.adapter.auth;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Legacy v1 auth role API. Loaded only when new default-auth-plugin is on classpath.
 *
 * @author nkorange
 * @deprecated Use v3 auth API instead.
 */
@RestController
@RequestMapping("/v1/auth/roles")
@Deprecated
public class LegacyAuthRoleController {

    private final NacosRoleService roleService;

    public LegacyAuthRoleController(NacosRoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * Get roles list (v1 API).
     */
    @GetMapping(params = "search=accurate")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/role/list")
    public Object getRoles(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", defaultValue = "") String username,
            @RequestParam(name = "role", defaultValue = "") String role) {
        return roleService.getRoles(username, role, pageNo, pageSize);
    }

    /**
     * Fuzzy search roles (v1 API).
     */
    @GetMapping(params = "search=blur")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/role/list")
    public Page<RoleInfo> fuzzySearchRole(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", defaultValue = "") String username,
            @RequestParam(name = "role", defaultValue = "") String role) {
        return roleService.findRoles(username, role, pageNo, pageSize);
    }

    /**
     * Search role names by prefix (v1 API).
     */
    @GetMapping("/search")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/role/search")
    public List<String> searchRoles(@RequestParam String role) {
        return roleService.findRoleNames(role);
    }

    /**
     * Add role to user (v1 API).
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "POST ${contextPath:nacos}/v3/auth/role")
    public Object addRole(@RequestParam String role, @RequestParam String username) {
        roleService.addRole(role, username);
        return RestResultUtils.success("add role ok!");
    }

    /**
     * Delete role or unbind role from user (v1 API).
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "DELETE ${contextPath:nacos}/v3/auth/role")
    public Object deleteRole(@RequestParam String role,
            @RequestParam(name = "username", defaultValue = StringUtils.EMPTY) String username) {
        if (StringUtils.isBlank(username)) {
            roleService.deleteRole(role);
        } else {
            roleService.deleteRole(role, username);
        }
        return RestResultUtils.success("delete role of user " + username + " ok!");
    }
}
