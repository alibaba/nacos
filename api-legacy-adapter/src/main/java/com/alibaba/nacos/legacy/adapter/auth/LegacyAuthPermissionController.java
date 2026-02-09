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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy v1 auth permission API. Loaded only when new default-auth-plugin is on classpath.
 *
 * @author nkorange
 * @deprecated Use v3 auth API instead.
 */
@RestController
@RequestMapping("/v1/auth/permissions")
@Deprecated
public class LegacyAuthPermissionController {
    
    private final NacosRoleService nacosRoleService;
    
    public LegacyAuthPermissionController(NacosRoleService nacosRoleService) {
        this.nacosRoleService = nacosRoleService;
    }
    
    /**
     * Get permissions of a role (v1 API).
     */
    @GetMapping(params = "search=accurate")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/permission/list")
    public Object getPermissions(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "role", defaultValue = StringUtils.EMPTY) String role) {
        return nacosRoleService.getPermissions(role, pageNo, pageSize);
    }
    
    /**
     * Fuzzy search permissions (v1 API).
     */
    @GetMapping(params = "search=blur")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/permission/list")
    public Page<PermissionInfo> fuzzySearchPermission(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "role", defaultValue = StringUtils.EMPTY) String role) {
        return nacosRoleService.findPermissions(role, pageNo, pageSize);
    }
    
    /**
     * Add permission to role (v1 API).
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "POST ${contextPath:nacos}/v3/auth/permission")
    public Object addPermission(@RequestParam String role, @RequestParam String resource, @RequestParam String action) {
        nacosRoleService.addPermission(role, resource, action);
        return RestResultUtils.success("add permission ok!");
    }
    
    /**
     * Delete permission from role (v1 API).
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "DELETE ${contextPath:nacos}/v3/auth/permission")
    public Object deletePermission(@RequestParam String role, @RequestParam String resource,
            @RequestParam String action) {
        nacosRoleService.deletePermission(role, resource, action);
        return RestResultUtils.success("delete permission ok!");
    }
    
    /**
     * Check if permission already exists (v1 API).
     */
    @GetMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/permission")
    public Result<Boolean> isDuplicatePermission(@RequestParam String role, @RequestParam String resource,
            @RequestParam String action) {
        return nacosRoleService.isDuplicatePermission(role, resource, action);
    }
}
