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
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
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
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName(username);
        String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier(namespaceId,
            resourceType, resourceName);
        // Scenario 1: the grantee already has the dedicated visibility role; only
        // ensure the requested permission row exists.
        if (userHasRole(username, roleName)) {
            grantPermission(roleName, resourceId, storedAction);
            return;
        }
        // Scenario 2: create the user's reserved visibility role binding first, then
        // attach the resource/action permission to that role.
        boolean roleAdded = false;
        try {
            roleService.addRole(roleName, username);
            roleAdded = true;
            grantPermission(roleName, resourceId, storedAction);
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

    private void grantPermission(String roleName, String resourceId, String storedAction) {
        if ("rw".equals(storedAction)
            && roleService.isDuplicatePermission(roleName, resourceId, "r").getData()) {
            // Write grants are persisted as "rw"; remove an older read-only row so
            // the same user/resource pair has one effective visibility grant.
            roleService.deletePermission(roleName, resourceId, "r");
        }
        if (!roleService.isDuplicatePermission(roleName, resourceId, storedAction).getData()) {
            roleService.addPermission(roleName, resourceId, storedAction);
        }
    }

    @Override
    public void revoke(String namespaceId, String resourceType, String resourceName,
        String username, String action) throws NacosException {
        VisibilityResource resource =
            requireManagedResource(namespaceId, resourceType, resourceName);
        checkManageGrantAuthority(resource);
        validateUsername(username);
        validateGranteeExists(username);
        String storedAction = normalizeGrantAction(action);
        String roleName = VisibilityGrantRoleHelper.buildUserRoleName(username);
        String resourceId = VisibilityGrantRoleHelper.buildResourceIdentifier(namespaceId,
            resourceType, resourceName);
        if (!roleService.isDuplicatePermission(roleName, resourceId, storedAction).getData()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "visibility permission does not exist for user '" + username + "'");
        }
        // Keep the empty internal role binding; without a matching permission row it
        // cannot grant visibility, and retaining it makes future grants idempotent.
        roleService.deletePermission(roleName, resourceId, storedAction);
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
        String dedicatedRoleName = VisibilityGrantRoleHelper.buildUserRoleName(username);
        boolean hasDedicatedRole =
            roles.stream().anyMatch(each -> dedicatedRoleName.equals(each.getRole()));
        if (!hasDedicatedRole) {
            return Collections.emptyList();
        }
        String resolvedNamespaceId = VisibilityGrantRoleHelper.normalizeNamespaceId(namespaceId);
        String normalizedResourceType =
            VisibilityGrantRoleHelper.normalizeResourceType(resourceType);
        Set<String> names = new LinkedHashSet<>();
        List<PermissionInfo> permissions = roleService.getPermissions(dedicatedRoleName);
        if (CollectionUtils.isEmpty(permissions)) {
            return Collections.emptyList();
        }
        for (PermissionInfo permission : permissions) {
            VisibilityGrantRoleHelper.ParsedGrantResource parsed =
                VisibilityGrantRoleHelper.tryParseResourceIdentifier(permission.getResource());
            if (parsed == null) {
                continue;
            }
            if (!resolvedNamespaceId.equals(parsed.getNamespaceId())
                || !normalizedResourceType.equals(parsed.getResourceType())) {
                continue;
            }
            if (VisibilityGrantRoleHelper.matchesRequestedAction(permission.getAction(),
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

}
