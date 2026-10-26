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
import com.alibaba.nacos.plugin.auth.impl.utils.AuthIdentityUtils;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.visibility.VisibilityGrantService;
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
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default visibility service implementation for Nacos auth plugin.
 *
 * @author xiweng.yy
 */
public class DefaultVisibilityService implements VisibilityService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisibilityService.class);
    
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
        if (isPermitted(identity, isRead, apiType, resource)) {
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
            advisor.setAuthorizedPredicate(buildAuthorizedResources(identity, action, context));
            return advisor;
        }
        advisor.setBasePredicate(isAnonymousIdentity(identity) ? BaseVisibilityPredicate.PUBLIC
            : BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        advisor.setAuthorizedPredicate(buildAuthorizedResources(identity, action, context));
        return advisor;
    }
    
    @Override
    public String resolveDefaultScopeForCreate(String identity, String apiType,
        String resourceType) {
        return "agent".equals(resourceType) || "mcp".equals(resourceType)
            ? VisibilityConstants.SCOPE_PUBLIC : VisibilityConstants.SCOPE_PRIVATE;
    }
    
    @Override
    public String getVisibilityServiceName() {
        return NAME;
    }
    
    private boolean isPermitted(String currentUser, boolean isRead, String apiType,
        VisibilityResource candidate) {
        if (isOwner(currentUser, candidate)) {
            return true;
        }
        if (isRead && VisibilityConstants.SCOPE_PUBLIC.equals(candidate.getScope())) {
            return true;
        }
        String action = isRead ? VisibilityConstants.ACTION_READ : VisibilityConstants.ACTION_WRITE;
        return checkResourcePermission(currentUser, apiType, candidate, action);
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
    
    private boolean checkResourcePermission(String identity, String apiType,
        VisibilityResource res, String action) {
        String resourceId = buildResourceIdentifier(res);
        Resource resource = new Resource("", "", resourceId, SignType.SPECIFIED, new Properties());
        Permission permission = new Permission(resource, action);
        try {
            Optional<AuthPluginService> authService = findAuthPluginService(apiType);
            if (authService.isPresent()) {
                IdentityContext context = permissionIdentity(identity, authService.get());
                return context != null
                    && authService.get().validateAuthority(context, permission).isSuccess();
            }
            return false;
        } catch (Exception e) {
            LOGGER.debug(
                "[DefaultVisibilityService] Permission check failed for resource '{}': {}",
                resourceId,
                e.getMessage());
            return false;
        }
    }
    
    private IdentityContext permissionIdentity(String identity, AuthPluginService service) {
        if (StringUtils.isBlank(identity) || isAnonymousIdentity(identity)) {
            return null;
        }
        if (service instanceof AbstractNacosAuthPluginService) {
            // The SPI identity was authenticated at the entry point. Re-evaluate current roles
            // without retaining credentials or borrowing another request's cached admin flag.
            IdentityContext context = new IdentityContext();
            context.setParameter(AuthConstants.NACOS_USER_KEY, new NacosUser(identity));
            context.setParameter(
                com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID,
                identity);
            return context;
        }
        // Foreign plugins may need their own authenticated context; never invent one for them.
        IdentityContext context =
            RequestContextHolder.getContext().getAuthContext().getIdentityContext();
        return context != null && identity.equals(context.getParameter(
            com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID)) ? context
                : null;
    }
    
    private Optional<AuthPluginService> findAuthPluginService(String apiType) {
        NacosAuthConfigHolder holder = NacosAuthConfigHolder.getInstance();
        if (StringUtils.isNotBlank(apiType)) {
            NacosAuthConfig config = holder.getNacosAuthConfigByScope(apiType);
            return config == null || !config.isAuthEnabled() ? Optional.empty()
                : AuthPluginManager.getInstance()
                    .findAuthServiceSpiImpl(config.getNacosAuthSystemType());
        }
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
        return AuthIdentityUtils.isCurrentIdentityGlobalAdmin(identity);
    }
    
    private AuthorizedResources buildAuthorizedResources(String identity, String action,
        VisibilityQueryContext context) {
        AuthorizedResources authorized = new AuthorizedResources();
        authorized.setResourceType(context == null ? null : context.getResourceType());
        authorized.setResources(new ArrayList<>());
        if (context == null || StringUtils.isBlank(identity)) {
            return authorized;
        }
        if (ApplicationUtils.getApplicationContext() == null) {
            // Unit tests and lightweight runtimes may call the advisor before Spring context is ready.
            return authorized;
        }
        AtomicReference<VisibilityGrantService> serviceRef = new AtomicReference<>();
        ApplicationUtils.getBeanIfExist(VisibilityGrantService.class, serviceRef::set);
        VisibilityGrantService grantService = serviceRef.get();
        if (grantService == null) {
            return authorized;
        }
        // The domain adapter combines these names with the base predicate before count and paging.
        List<String> resources = grantService.findAuthorizedResourceNames(identity,
            context.getNamespaceId(), context.getResourceType(), action);
        authorized.setResources(new ArrayList<>(resources));
        return authorized;
    }
}
