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

package com.alibaba.nacos.console.controller.v3.auth;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.auth.PermissionProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling HTTP requests related to permission operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@RestController
@RequestMapping("/v3/console/auth/permission")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConsolePermissionController {
    
    private final PermissionProxy permissionProxy;
    
    /**
     * Constructs a new ConsolePermissionController with the provided PermissionProxy.
     *
     * @param permissionProxy the proxy used for handling permission-related operations
     */
    @Autowired
    public ConsolePermissionController(PermissionProxy permissionProxy) {
        this.permissionProxy = permissionProxy;
    }
    
    /**
     * Add a permission to a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return ok if succeed
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.WRITE)
    public Object createPermission(@RequestParam String role, @RequestParam String resource, @RequestParam String action) {
        permissionProxy.createPermission(role, resource, action);
        return Result.success("add permission ok!");
    }
    
    
    /**
     * Delete a permission from a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return ok if succeed
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.WRITE)
    public Object deletePermission(@RequestParam String role, @RequestParam String resource,
            @RequestParam String action) {
        permissionProxy.deletePermission(role, resource, action);
        return Result.success("delete permission ok!");
    }
    
    /**
     * Query permissions of a role.
     *
     * @param role     the role
     * @param pageNo   page index
     * @param pageSize page size
     * @param search the type of search (accurate or blur)
     * @return permission of a role
     */
    @GetMapping("/list")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "permissions", action = ActionTypes.READ)
    public  Result<Page<PermissionInfo>> getPermissionList(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "role", defaultValue = StringUtils.EMPTY) String role,
            @RequestParam(name = "search", defaultValue = "accurate") String search) {
        Page<PermissionInfo> permissionPage = permissionProxy.getPermissionList(role, pageNo, pageSize, search);
        return Result.success(permissionPage);
    }

}
