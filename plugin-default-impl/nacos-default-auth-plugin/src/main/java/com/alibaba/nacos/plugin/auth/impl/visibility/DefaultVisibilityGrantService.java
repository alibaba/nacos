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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.utils.AuthIdentityUtils;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityResourceLocator;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link VisibilityGrantService}.
 *
 * @author Zhengcy05
 */
public class DefaultVisibilityGrantService implements VisibilityGrantService {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    private final NacosRoleService roleService;
    
    private final NacosUserService userService;
    
    public DefaultVisibilityGrantService(NacosRoleService roleService,
        NacosUserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }
    
    @Override
    public void grant(String namespaceId, String resourceType, String resourceName, String username,
        String action) throws NacosException {
        VisibilityResource resource =
            requireManagedResource(namespaceId, resourceType, resourceName);
        checkManageGrantAuthority(resource);
        validateUsername(username);
        validateGranteeExists(username);
        String storedAction = normalizeGrantAction(action);
        String roleName = VisibilityGrantRoleHelper.buildRoleName(namespaceId, resourceType,
            resourceName, storedAction);
        String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier(namespaceId,
            resourceType, resourceName);
        // Scenario 1: The user has already bound this authorized role; simply ensure that the role has permission
        if (userHasRole(username, roleName)) {
            // Permissions are shared by the internal grant role, so an existing binding only
            // needs the backing permission row to be present.
            if (!roleService.isDuplicatePermission(roleName, resourceId, storedAction).getData()) {
                roleService.addPermission(roleName, resourceId, storedAction);
            }
            return;
        }
        // Scenario 2: User not bound to a role, create a new role + bind the user + bind permissions, ensuring atomic operations.
        boolean roleAdded = false;
        try {
            roleService.addRole(roleName, username);
            roleAdded = true;
            if (!roleService.isDuplicatePermission(roleName, resourceId, storedAction).getData()) {
                roleService.addPermission(roleName, resourceId, storedAction);
            }
        } catch (RuntimeException e) {
            if (roleAdded) {
                try {
                    // Keep role and permission creation effectively atomic for the grant API.
                    roleService.deleteRole(roleName, username);
                } catch (RuntimeException ignore) {
                }
            }
            throw e;
        }
    }
    
    @Override
    public void revoke(String namespaceId, String resourceType, String resourceName,
        String username, String action) throws NacosException {
        VisibilityResource resource =
            requireManagedResource(namespaceId, resourceType, resourceName);
        checkManageGrantAuthority(resource);
        validateUsername(username);
        String storedAction = normalizeGrantAction(action);
        String roleName = VisibilityGrantRoleHelper.buildRoleName(namespaceId, resourceType,
            resourceName, storedAction);
        roleService.deleteRole(roleName, username);
        if (!roleHasBindings(roleName)) {
            // Drop the shared permission row only after the last grantee is removed.
            roleService.deletePermission(roleName,
                VisibilityGrantRoleHelper.buildResourceIdentifier(namespaceId, resourceType,
                    resourceName),
                storedAction);
        }
    }
    
    @Override
    public List<VisibilityGrantInfo> list(String namespaceId, String resourceType,
        String resourceName) throws NacosException {
        VisibilityResource resource =
            requireManagedResource(namespaceId, resourceType, resourceName);
        checkManageGrantAuthority(resource);
        String rolePrefix = VisibilityGrantRoleHelper.buildRolePrefix(namespaceId, resourceType,
            resourceName);
        Page<RoleInfo> rolePage =
            roleService.findRoles(StringUtils.EMPTY, rolePrefix, DEFAULT_PAGE_NO,
                Integer.MAX_VALUE);
        List<VisibilityGrantInfo> result = new ArrayList<>();
        if (rolePage == null || CollectionUtils.isEmpty(rolePage.getPageItems())) {
            return result;
        }
        for (RoleInfo roleInfo : rolePage.getPageItems()) {
            VisibilityGrantRoleHelper.ParsedGrantRole parsed =
                VisibilityGrantRoleHelper.tryParse(roleInfo.getRole());
            if (parsed == null || !roleInfo.getRole().startsWith(rolePrefix)) {
                continue;
            }
            VisibilityGrantInfo item = new VisibilityGrantInfo();
            item.setNamespaceId(parsed.getNamespaceId());
            item.setResourceType(parsed.getResourceType());
            item.setResourceName(parsed.getResourceName());
            item.setUsername(roleInfo.getUsername());
            item.setAction(parsed.getStoredAction());
            result.add(item);
        }
        result.sort(Comparator.comparing(VisibilityGrantInfo::getUsername)
            .thenComparing(VisibilityGrantInfo::getAction));
        return result;
    }
    
    // Query the names of all resources that a specified user has visibility permissions for,
    // under a specified namespace, resource type, and action.
    @Override
    public List<String> findAuthorizedResourceNames(String username, String namespaceId,
        String resourceType, String action) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(resourceType)
            || StringUtils.isBlank(action)) {
            return Collections.emptyList();
        }
        List<RoleInfo> roles = roleService.getRoles(username);
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptyList();
        }
        String resolvedNamespaceId = VisibilityGrantRoleHelper.normalizeNamespaceId(namespaceId);
        String normalizedResourceType =
            VisibilityGrantRoleHelper.normalizeResourceType(resourceType);
        Set<String> names = new LinkedHashSet<>();
        for (RoleInfo role : roles) {
            VisibilityGrantRoleHelper.ParsedGrantRole parsed =
                VisibilityGrantRoleHelper.tryParse(role.getRole());
            if (parsed == null) {
                continue;
            }
            if (!resolvedNamespaceId.equals(parsed.getNamespaceId())
                || !normalizedResourceType.equals(parsed.getResourceType())) {
                continue;
            }
            if (VisibilityGrantRoleHelper.matchesRequestedAction(parsed.getStoredAction(),
                action)) {
                names.add(parsed.getResourceName());
            }
        }
        return new ArrayList<>(names);
    }
    
    private VisibilityResource requireManagedResource(String namespaceId, String resourceType,
        String resourceName) throws NacosException {
        validateResourceTypeAndName(resourceType, resourceName);
        AtomicReference<VisibilityResourceLocator> locatorRef = new AtomicReference<>();
        ApplicationUtils.getBeanIfExist(VisibilityResourceLocator.class, locatorRef::set);
        VisibilityResourceLocator locator = locatorRef.get();
        if (locator == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "visibility grant management is unsupported in current runtime");
        }
        Optional<VisibilityResource> resource =
            locator.findResource(VisibilityGrantRoleHelper.normalizeNamespaceId(namespaceId),
                VisibilityGrantRoleHelper.normalizeResourceType(resourceType), resourceName);
        return resource.orElseThrow(() -> new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND,
            "resource not found: " + resourceName));
    }
    
    private void checkManageGrantAuthority(VisibilityResource resource) throws NacosException {
        // Allow access rules: 1. Authentication not enabled; 2. Global administrator; 3. Resource owner.
        if (!NacosAuthConfigHolder.getInstance().isAnyAuthEnabled()) {
            return;
        }
        String currentUsername = AuthIdentityUtils.resolveCurrentUsername();
        if (AuthIdentityUtils.isCurrentIdentityGlobalAdmin(currentUsername)) {
            return;
        }
        if (StringUtils.isNotBlank(currentUsername)
            && currentUsername.equals(resource.getOwner())) {
            return;
        }
        throw new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
            "No permission to manage visibility grants for resource: "
                + resource.getResourceName());
    }
    
    private void validateResourceTypeAndName(String resourceType, String resourceName)
        throws NacosException {
        if (StringUtils.isBlank(resourceType)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "resourceType is blank");
        }
        if (StringUtils.isBlank(resourceName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "resourceName is blank");
        }
    }
    
    private void validateUsername(String username) throws NacosException {
        if (StringUtils.isBlank(username)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "username is blank");
        }
    }
    
    private void validateGranteeExists(String username) throws NacosException {
        User grantee = userService.getUser(username);
        if (grantee == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "user '" + username + "' not found");
        }
    }
    
    private String normalizeGrantAction(String action) throws NacosException {
        try {
            // Persist write grants as "rw" so write authorization can imply read visibility.
            return VisibilityGrantRoleHelper.normalizeStoredAction(action);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
        }
    }
    
    private boolean userHasRole(String username, String roleName) {
        List<RoleInfo> roles = roleService.getRoles(username);
        if (CollectionUtils.isEmpty(roles)) {
            return false;
        }
        return roles.stream().anyMatch(each -> roleName.equals(each.getRole()));
    }
    
    private boolean roleHasBindings(String roleName) {
        Page<RoleInfo> rolePage =
            roleService.getRoles(StringUtils.EMPTY, roleName, DEFAULT_PAGE_NO, 1);
        return rolePage != null && CollectionUtils.isNotEmpty(rolePage.getPageItems());
    }
}
