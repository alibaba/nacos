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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.Optional;
import java.util.function.Function;

/**
 * Permission checker for config clone source namespace read authorization.
 *
 * @author xiweng.yy
 */
@Service
public class ConfigCloneSourceReadPermissionChecker {

    private final Function<String, NacosAuthConfig> authConfigProvider;

    private final Function<String, Optional<AuthPluginService>> authPluginProvider;

    public ConfigCloneSourceReadPermissionChecker() {
        this(apiType -> NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(apiType),
            authType -> AuthPluginManager.getInstance().findAuthServiceSpiImpl(authType));
    }

    ConfigCloneSourceReadPermissionChecker(
        Function<String, NacosAuthConfig> authConfigProvider,
        Function<String, Optional<AuthPluginService>> authPluginProvider) {
        this.authConfigProvider = authConfigProvider;
        this.authPluginProvider = authPluginProvider;
    }

    /**
     * Check whether current request identity can read configs in the clone source namespace.
     *
     * @param sourceNamespaceId source namespace ID.
     * @throws AccessException if current identity has no read permission.
     */
    public void checkSourceReadPermission(String sourceNamespaceId) throws AccessException {
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        String apiType = authContext.getApiType();
        if (!isAuthEnabled(apiType)) {
            return;
        }
        if (!enableReadAuth(apiType)) {
            return;
        }
        IdentityContext identityContext = authContext.getIdentityContext();
        if (identityContext == null) {
            return;
        }
        if (isServerIdentity(identityContext)) {
            return;
        }
        AuthResult<?> authResult = validate(apiType, identityContext,
            buildNamespaceReadPermission(sourceNamespaceId));
        if (!authResult.isSuccess()) {
            throw new AccessException(authResult.format());
        }
    }
    
    private Permission buildNamespaceReadPermission(String namespaceId) {
        Properties properties = new Properties();
        properties.put(Constants.Resource.ACTION, ActionTypes.READ.toString());
        Resource resource = new Resource(namespaceId, StringUtils.EMPTY, StringUtils.EMPTY,
            SignType.CONFIG, properties);
        return new Permission(resource, ActionTypes.READ.toString());
    }
    
    private boolean isServerIdentity(IdentityContext identityContext) {
        return identityContext.getParameter(Constants.Identity.SERVER_IDENTITY, Boolean.FALSE);
    }
    
    private boolean isAuthEnabled(String apiType) {
        NacosAuthConfig authConfig = getAuthConfig(apiType);
        return authConfig != null && authConfig.isAuthEnabled();
    }
    
    private boolean enableReadAuth(String apiType) {
        Optional<AuthPluginService> authPluginService = findAuthPluginService(apiType);
        return authPluginService
            .map(service -> service.enableAuth(ActionTypes.READ, SignType.CONFIG))
            .orElse(false);
    }
    
    private AuthResult<?> validate(String apiType, IdentityContext identityContext,
        Permission permission)
        throws AccessException {
        Optional<AuthPluginService> authPluginService = findAuthPluginService(apiType);
        if (authPluginService.isPresent()) {
            return authPluginService.get().validateAuthority(identityContext, permission);
        }
        return AuthResult.successResult();
    }
    
    private Optional<AuthPluginService> findAuthPluginService(String apiType) {
        NacosAuthConfig authConfig = getAuthConfig(apiType);
        if (authConfig == null || StringUtils.isBlank(authConfig.getNacosAuthSystemType())) {
            return Optional.empty();
        }
        return authPluginProvider.apply(authConfig.getNacosAuthSystemType());
    }
    
    private NacosAuthConfig getAuthConfig(String apiType) {
        if (StringUtils.isBlank(apiType)) {
            return null;
        }
        return authConfigProvider.apply(apiType);
    }
}
