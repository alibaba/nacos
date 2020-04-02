/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.console.controller;


import com.alibaba.nacos.config.server.model.RestResult;
import com.alibaba.nacos.console.security.nacos.NacosAuthConfig;
import com.alibaba.nacos.console.security.nacos.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Role operation controller
 *
 * @author nkorange
 * @since 1.2.0
 */
@RestController
@RequestMapping("/v1/auth/roles")
public class RoleController {

    @Autowired
    private NacosRoleServiceImpl roleService;

    /**
     * Get roles list
     *
     * @param pageNo   number index of page
     * @param pageSize page size
     * @param username optional, username of user
     * @return role list
     */
    @GetMapping
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.READ)
    public Object getRoles(@RequestParam int pageNo, @RequestParam int pageSize,
                           @RequestParam(name = "username", defaultValue = "") String username) {
        return roleService.getRolesFromDatabase(username, pageNo, pageSize);
    }

    /**
     * Add a role to a user
     * <p>
     * This method is used for 2 functions:
     * 1. create a role and bind it to GLOBAL_ADMIN.
     * 2. bind a role to an user.
     *
     * @param role
     * @param username
     * @return
     */
    @PostMapping
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.WRITE)
    public Object addRole(@RequestParam String role, @RequestParam String username) {
        roleService.addRole(role, username);
        return new RestResult<>(200, "add role ok!");
    }

    /**
     * Delete a role. If no username is specified, all users under this role are deleted
     *
     * @param role     role
     * @param username username
     * @return ok if succeed
     */
    @DeleteMapping
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "roles", action = ActionTypes.WRITE)
    public Object deleteRole(@RequestParam String role,
                             @RequestParam(name = "username", defaultValue = StringUtils.EMPTY) String username) {
        if (StringUtils.isBlank(username)) {
            roleService.deleteRole(role);
        } else {
            roleService.deleteRole(role, username);
        }
        return new RestResult<>(200, "delete role of user " + username + " ok!");
    }

}
