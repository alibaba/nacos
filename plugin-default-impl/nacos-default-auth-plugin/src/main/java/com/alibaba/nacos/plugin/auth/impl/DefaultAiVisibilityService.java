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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.AuthorizedResources;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

/**
 * Default AI visibility service implementation for nacos auth plugin.
 *
 * @author xiweng.yy
 */
public class DefaultAiVisibilityService implements VisibilityService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAiVisibilityService.class);
    
    private static final String NAME = AuthConstants.AUTH_PLUGIN_TYPE;
    
    private static final String RESOURCE_PREFIX = "@@visibility";
    
    private static final String ANONYMOUS_IDENTITY = AuthConstants.ANONYMOUS_USER;
    
    @Override
    public ValidationResult validateVisibility(String identity, String action, String apiType,
        VisibilityResource resource) {
        if (isAuthDisabled(apiType)) {
            return ValidationResult.allow();
        }
        if (isCurrentIdentityGlobalAdmin(identity)) {
            return ValidationResult.allow();
        }
        boolean isRead = VisibilityConstants.ACTION_READ.equals(action);
        if (isPermitted(identity, isRead, resource)) {
            return ValidationResult.allow();
        }
        return ValidationResult
            .deny("No visibility permission for resource: " + resource.getResourceName());
    }
    
    @Override
    public QueryAdvisor adviseQuery(String identity, String action, String apiType,
        VisibilityQueryContext context) {
        QueryAdvisor advisor = new QueryAdvisor();
        if (isAuthDisabled(apiType) || isCurrentIdentityGlobalAdmin(identity)) {
            advisor.setBasePredicate(BaseVisibilityPredicate.ALL);
            return advisor;
        }
        if (!VisibilityConstants.ACTION_READ.equals(action)) {
            advisor.setBasePredicate(BaseVisibilityPredicate.OWNER);
            return advisor;
        }
        advisor.setBasePredicate(isAnonymousIdentity(identity) ? BaseVisibilityPredicate.PUBLIC
            : BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        AuthorizedResources authorized = new AuthorizedResources();
        authorized.setResourceType(context == null ? null : context.getResourceType());
        // TODO: populate explicit authorized resources from auth plugin once query advisor integration is complete.
        authorized.setResources(new ArrayList<>());
        advisor.setAuthorizedPredicate(authorized);
        return advisor;
    }
    
    @Override
    public String getVisibilityServiceName() {
        return NAME;
    }
    
    private boolean isPermitted(String currentUser, boolean isRead, VisibilityResource candidate) {
        if (isOwner(currentUser, candidate)) {
            return true;
        }
        if (isRead && VisibilityConstants.SCOPE_PUBLIC.equals(candidate.getScope())) {
            return true;
        }
        String action = isRead ? VisibilityConstants.ACTION_READ : VisibilityConstants.ACTION_WRITE;
        return checkResourcePermission(candidate, action);
    }
    
    private boolean isOwner(String currentUser, VisibilityResource resource) {
        return StringUtils.isNotBlank(currentUser) && currentUser.equals(resource.getOwner());
    }
    
    private String buildResourceIdentifier(VisibilityResource res) {
        String ns = StringUtils.isBlank(res.getNamespaceId()) ? Constants.DEFAULT_NAMESPACE_ID
            : res.getNamespaceId();
        return RESOURCE_PREFIX + "/" + ns + "/" + res.getResourceType() + "/"
            + res.getResourceName();
    }
    
    private boolean checkResourcePermission(VisibilityResource res, String action) {
        String resourceId = buildResourceIdentifier(res);
        Resource resource = new Resource("", "", resourceId, SignType.SPECIFIED, new Properties());
        Permission permission = new Permission(resource, action);
        try {
            Optional<AuthPluginService> authService = findAuthPluginService();
            if (authService.isPresent()) {
                IdentityContext identity =
                    RequestContextHolder.getContext().getAuthContext().getIdentityContext();
                return authService.get().validateAuthority(identity, permission).isSuccess();
            }
            return false;
        } catch (Exception e) {
            LOGGER.debug(
                "[DefaultAiVisibilityService] Permission check failed for resource '{}': {}",
                resourceId,
                e.getMessage());
            return false;
        }
    }
    
    private Optional<AuthPluginService> findAuthPluginService() {
        NacosAuthConfigHolder holder = NacosAuthConfigHolder.getInstance();
        for (NacosAuthConfig config : holder.getAllNacosAuthConfig()) {
            if (config.isAuthEnabled()) {
                return AuthPluginManager.getInstance()
                    .findAuthServiceSpiImpl(config.getNacosAuthSystemType());
            }
        }
        return Optional.empty();
    }
    
    private boolean isAuthDisabled(String apiType) {
        if (StringUtils.isBlank(apiType)) {
            return !NacosAuthConfigHolder.getInstance().isAnyAuthEnabled();
        }
        NacosAuthConfig authConfig =
            NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(apiType);
        return authConfig == null || !authConfig.isAuthEnabled();
    }
    
    private boolean isAnonymousIdentity(String identity) {
        return ANONYMOUS_IDENTITY.equals(identity);
    }
    
    private boolean isCurrentIdentityGlobalAdmin(String identity) {
        if (StringUtils.isBlank(identity)) {
            return false;
        }
        try {
            IdentityContext identityContext =
                RequestContextHolder.getContext().getAuthContext().getIdentityContext();
            Object nacosUser = identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
            if (!(nacosUser instanceof NacosUser user)) {
                return false;
            }
            return identity.equals(user.getUserName()) && user.isGlobalAdmin();
        } catch (Exception e) {
            return false;
        }
    }
}
