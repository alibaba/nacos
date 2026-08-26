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

package com.alibaba.nacos.ai.service.mcp;

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

import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Re-authorizes a deprecated ID-only request against its resolved canonical MCP name.
 *
 * <p>The normal protocol filter authenticates the caller first. This service then performs exact
 * canonical identity and authority validation before any lifecycle read or mutation.</p>
 *
 * @author Nacos
 */
@Service
public class McpCanonicalAuthorizationService {
    
    private final Function<String, NacosAuthConfig> authConfigProvider;
    
    private final Function<String, Optional<AuthPluginService>> authPluginProvider;
    
    public McpCanonicalAuthorizationService() {
        this(apiType -> NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(apiType),
            authType -> AuthPluginManager.getInstance().findAuthServiceSpiImpl(authType));
    }
    
    McpCanonicalAuthorizationService(Function<String, NacosAuthConfig> authConfigProvider,
        Function<String, Optional<AuthPluginService>> authPluginProvider) {
        this.authConfigProvider = authConfigProvider;
        this.authPluginProvider = authPluginProvider;
    }
    
    /**
     * Re-authorize only when the compatibility request omitted the canonical name.
     *
     * @param namespaceId canonical namespace
     * @param canonicalName resolved canonical MCP name
     * @param requestedName name supplied by the caller
     * @param requestedId deprecated ID supplied by the caller
     * @param action requested action
     * @throws AccessException when canonical identity or authority validation fails
     */
    public void authorizeIdOnly(String namespaceId, String canonicalName, String requestedName,
        String requestedId, ActionTypes action) throws AccessException {
        if (StringUtils.isNotBlank(requestedName) || StringUtils.isBlank(requestedId)) {
            return;
        }
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        NacosAuthConfig authConfig = getAuthConfig(authContext.getApiType());
        if (authConfig == null || !authConfig.isAuthEnabled()) {
            return;
        }
        Optional<AuthPluginService> plugin = findAuthPluginService(authConfig);
        if (plugin.isEmpty() || !plugin.get().enableAuth(action, SignType.AI)) {
            return;
        }
        IdentityContext identity = authContext.getIdentityContext();
        if (identity == null || identity.getParameter(
            Constants.Identity.SERVER_IDENTITY, Boolean.FALSE)) {
            return;
        }
        Resource resource = buildResource(namespaceId, canonicalName, action);
        AuthResult<?> result = plugin.get().validateIdentity(identity, resource);
        authContext.setResource(resource);
        authContext.setAuthResult(result);
        if (!result.isSuccess()) {
            throw new AccessException(result.format());
        }
        result = plugin.get().validateAuthority(identity,
            new Permission(resource, action.toString()));
        authContext.setAuthResult(result);
        if (!result.isSuccess()) {
            throw new AccessException(result.format());
        }
    }
    
    private Resource buildResource(String namespaceId, String canonicalName, ActionTypes action) {
        Properties properties = new Properties();
        properties.setProperty(Constants.Resource.AI_TYPE,
            Constants.Resource.AI_TYPE_MCP);
        properties.setProperty(Constants.Resource.ACTION, action.toString());
        return new Resource(namespaceId,
            com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, canonicalName, SignType.AI,
            properties);
    }
    
    private Optional<AuthPluginService> findAuthPluginService(NacosAuthConfig authConfig) {
        if (StringUtils.isBlank(authConfig.getNacosAuthSystemType())) {
            return Optional.empty();
        }
        return authPluginProvider.apply(authConfig.getNacosAuthSystemType());
    }
    
    private NacosAuthConfig getAuthConfig(String apiType) {
        return StringUtils.isBlank(apiType) ? null : authConfigProvider.apply(apiType);
    }
}
