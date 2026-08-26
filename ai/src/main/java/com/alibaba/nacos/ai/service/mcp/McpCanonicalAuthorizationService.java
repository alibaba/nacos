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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_MCP;

/**
 * Rechecks MCP authority after a compatibility ID has been resolved to its canonical name.
 *
 * <p>The regular web filter still authenticates and authorizes the incoming request first. This
 * second, stricter check prevents a deprecated ID-only request from becoming an authorization
 * key or bypassing the permission attached to the canonical MCP name.</p>
 *
 * @author Nacos
 */
@Service
public class McpCanonicalAuthorizationService {
    
    private final Function<String, NacosAuthConfig> authConfigProvider;
    
    private final Function<String, Optional<AuthPluginService>> authPluginProvider;
    
    /**
     * Create the production authorization service backed by the global auth registries.
     */
    public McpCanonicalAuthorizationService() {
        this(scope -> NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(scope),
            authSystem -> AuthPluginManager.getInstance().findAuthServiceSpiImpl(authSystem));
    }
    
    McpCanonicalAuthorizationService(Function<String, NacosAuthConfig> authConfigProvider,
        Function<String, Optional<AuthPluginService>> authPluginProvider) {
        this.authConfigProvider = authConfigProvider;
        this.authPluginProvider = authPluginProvider;
    }
    
    /**
     * Authorize a canonical MCP read in the current request context.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @throws NacosException when the current identity lacks read permission
     */
    public void authorizeRead(String namespaceId, String mcpName) throws NacosException {
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        NacosAuthConfig authConfig = authConfigProvider.apply(authContext.getApiType());
        if (authConfig == null || !authConfig.isAuthEnabled()) {
            return;
        }
        Optional<AuthPluginService> plugin = authPluginProvider.apply(
            authConfig.getNacosAuthSystemType());
        if (plugin.isEmpty() || !plugin.get().enableAuth(ActionTypes.READ, SignType.AI)) {
            return;
        }
        IdentityContext identity = authContext.getIdentityContext();
        if (identity == null) {
            throw accessDenied("Missing authenticated identity for MCP authorization");
        }
        Resource resource = canonicalResource(namespaceId, mcpName);
        try {
            AuthResult result = plugin.get().validateAuthority(identity,
                new Permission(resource, ActionTypes.READ.toString()));
            if (!result.isSuccess()) {
                throw accessDenied(result.format());
            }
        } catch (AccessException e) {
            throw accessDenied(e.getErrMsg());
        }
        authContext.setResource(resource);
    }
    
    private Resource canonicalResource(String namespaceId, String mcpName) {
        Properties properties = new Properties();
        properties.setProperty(AI_TYPE, AI_TYPE_MCP);
        return new Resource(namespaceId, com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP,
            mcpName, SignType.AI, properties);
    }
    
    private NacosApiException accessDenied(String message) {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED, message);
    }
}
