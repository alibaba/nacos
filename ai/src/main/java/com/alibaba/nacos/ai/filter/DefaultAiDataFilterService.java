/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.filter;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.datafilter.constant.DataFilterConstants;
import com.alibaba.nacos.plugin.datafilter.model.FilterableResource;
import com.alibaba.nacos.plugin.datafilter.spi.DataFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Default {@link DataFilterService} implementation for AI module, paired with the default Nacos auth plugin (and
 * LDAP).
 *
 * <p>Uses {@code SignType.SPECIFIED} with colon-free resource identifier ({@code @@ai_private/namespace/type/name}) to
 * prevent wildcard penetration from standard namespace-level permissions.
 *
 * @author xiweng.yy
 */
public class DefaultAiDataFilterService implements DataFilterService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAiDataFilterService.class);
    
    private static final String NAME = "nacos-default-ai";
    
    private static final String RESOURCE_PREFIX = "@@ai_private";
    
    @Override
    public String getFilterServiceName() {
        return NAME;
    }
    
    @Override
    public <T extends FilterableResource> List<T> filter(String currentUser, String action, String apiType,
            List<T> candidates) {
        if (!isAuthEnabled(apiType)) {
            return candidates;
        }
        boolean isRead = DataFilterConstants.ACTION_READ.equals(action);
        List<T> result = new ArrayList<>();
        for (T candidate : candidates) {
            if (isPermitted(currentUser, isRead, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
    
    private boolean isPermitted(String currentUser, boolean isRead, FilterableResource candidate) {
        if (isOwner(currentUser, candidate)) {
            return true;
        }
        if (isRead && DataFilterConstants.SCOPE_PUBLIC.equals(candidate.getScope())) {
            return true;
        }
        String action = isRead ? DataFilterConstants.ACTION_READ : DataFilterConstants.ACTION_WRITE;
        return checkResourcePermission(candidate, action);
    }
    
    private boolean isOwner(String currentUser, FilterableResource resource) {
        return StringUtils.isNotBlank(currentUser) && currentUser.equals(resource.getOwner());
    }
    
    /**
     * Build a colon-free resource identifier to prevent wildcard penetration.
     */
    private String buildResourceIdentifier(FilterableResource res) {
        String ns = StringUtils.isBlank(res.getNamespaceId()) ? "public" : res.getNamespaceId();
        return RESOURCE_PREFIX + "/" + ns + "/" + res.getResourceType() + "/" + res.getResourceName();
    }
    
    /**
     * Validate permission through the auth plugin using {@code SignType.SPECIFIED} so that {@code joinResource()}
     * returns the name directly, bypassing the colon-based three-segment format.
     *
     * <p>Global admin users pass automatically because the default auth plugin's role checking grants
     * all permissions to {@code GLOBAL_ADMIN_ROLE} before pattern matching.
     */
    private boolean checkResourcePermission(FilterableResource res, String action) {
        String resourceId = buildResourceIdentifier(res);
        Resource resource = new Resource("", "", resourceId, SignType.SPECIFIED, new Properties());
        Permission permission = new Permission(resource, action);
        try {
            Optional<AuthPluginService> authService = findAuthPluginService();
            if (authService.isPresent()) {
                IdentityContext identity = RequestContextHolder.getContext().getAuthContext().getIdentityContext();
                return authService.get().validateAuthority(identity, permission).isSuccess();
            }
            return false;
        } catch (Exception e) {
            LOGGER.debug("[DefaultAiDataFilterService] Permission check failed for resource '{}': {}", resourceId,
                    e.getMessage());
            return false;
        }
    }
    
    private Optional<AuthPluginService> findAuthPluginService() {
        NacosAuthConfigHolder holder = NacosAuthConfigHolder.getInstance();
        for (NacosAuthConfig config : holder.getAllNacosAuthConfig()) {
            if (config.isAuthEnabled()) {
                return AuthPluginManager.getInstance().findAuthServiceSpiImpl(config.getNacosAuthSystemType());
            }
        }
        return Optional.empty();
    }
    
    private boolean isAuthEnabled(String apiType) {
        if (StringUtils.isBlank(apiType)) {
            return NacosAuthConfigHolder.getInstance().isAnyAuthEnabled();
        }
        NacosAuthConfig authConfig = NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(apiType);
        return authConfig != null && authConfig.isAuthEnabled();
    }
}
