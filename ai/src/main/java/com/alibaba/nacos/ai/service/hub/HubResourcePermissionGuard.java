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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
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
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.springframework.stereotype.Component;

import java.util.Properties;

/** Explicit typed source/target permissions, independent of admission to the shared directory. */
@Component
public class HubResourcePermissionGuard {
    
    private final HubSkillReadPermissionGuard readAdmission;
    
    private final HubSkillPermissionGuard governance;
    
    public HubResourcePermissionGuard(HubSkillReadPermissionGuard readAdmission,
        HubSkillPermissionGuard governance) {
        this.readAdmission = readAdmission;
        this.governance = governance;
    }
    
    /** Shared reads use the existing configured anonymous/authenticated admission policy. */
    public void requireHubRead() throws NacosException {
        readAdmission.requireHubRead();
    }
    
    /** Source READ requires both the ordinary auth authority and source visibility. */
    public void requireSourceRead(HubResourceType type, AiResource source) throws NacosException {
        requireSource(type, source, ActionTypes.READ);
    }
    
    /** Source WRITE does not follow from Hub READ or from the system Hub owner. */
    public void requireSourceWrite(HubResourceType type, AiResource source) throws NacosException {
        requireSource(type, source, ActionTypes.WRITE);
    }
    
    /** A nonexistent target requires explicit WRITE authority on the proposed identity. */
    public void requireTargetWrite(HubResourceType type, String namespaceId, String name)
        throws NacosException {
        if (type == null || StringUtils.isBlank(namespaceId) || StringUtils.isBlank(name)
            || HubNamespaceUtils.isHubNamespace(namespaceId)) {
            throw denied();
        }
        requireAuthority(type, namespaceId, name, ActionTypes.WRITE);
    }
    
    /** An explicitly chosen MCP target dependency needs its own Naming READ grant. */
    public void requireNamingRead(String namespaceId, String group, String name)
        throws NacosException {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(group)
            || StringUtils.isBlank(name) || HubNamespaceUtils.isHubNamespace(namespaceId)) {
            throw denied();
        }
        Properties properties = new Properties();
        properties.setProperty(Constants.Resource.ACTION, ActionTypes.READ.toString());
        requireAuthority(new Resource(namespaceId, group, name, SignType.NAMING, properties),
            ActionTypes.READ);
    }
    
    /** Governance remains the existing independent administrator grant. */
    public void requireAdministrator() throws NacosException {
        governance.requireAdministrator();
    }
    
    private void requireSource(HubResourceType type, AiResource source, ActionTypes action)
        throws NacosException {
        if (type == null || source == null || source.getId() == null || source.getId() <= 0
            || !type.resourceType().equals(source.getType())
            || StringUtils.isBlank(source.getNamespaceId()) || StringUtils.isBlank(source.getName())
            || HubNamespaceUtils.isHubNamespace(source.getNamespaceId())) {
            throw denied();
        }
        String principal =
            requireAuthority(type, source.getNamespaceId(), source.getName(), action);
        try {
            VisibilityService visibility = VisibilityHelper.findVisibilityService()
                .orElseThrow(this::denied);
            String apiType = RequestContextHolder.getContext().getAuthContext().getApiType();
            ValidationResult result = visibility.validateVisibility(principal, action.toString(),
                apiType, source);
            if (result == null || !result.isAllowed()) {
                throw denied();
            }
        } catch (RuntimeException unavailable) {
            throw denied();
        }
    }
    
    private String requireAuthority(HubResourceType type, String namespaceId, String name,
        ActionTypes action) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(Constants.Resource.AI_TYPE, type == HubResourceType.AGENTSPEC
            ? Constants.Resource.AI_TYPE_AGENT_SPEC : type.resourceType());
        properties.setProperty(Constants.Resource.ACTION, action.toString());
        Resource resource = new Resource(namespaceId,
            com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, name, SignType.AI,
            properties);
        return requireAuthority(resource, action);
    }
    
    private String requireAuthority(Resource resource, ActionTypes action) throws NacosException {
        readAdmission.requireHubRead();
        try {
            AuthContext auth = RequestContextHolder.getContext().getAuthContext();
            if (!ApiType.CONSOLE_API.name().equals(auth.getApiType())
                && !ApiType.ADMIN_API.name().equals(auth.getApiType())) {
                throw denied();
            }
            NacosAuthConfig config = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(auth.getApiType());
            IdentityContext identity = auth.getIdentityContext();
            if (config == null || !config.isAuthEnabled() || identity == null
                || auth.getAuthResult() == null || !auth.getAuthResult().isSuccess()
                || Boolean.parseBoolean(String.valueOf(
                    identity.getParameter(Constants.Identity.SERVER_IDENTITY)))) {
                throw denied();
            }
            Object principal = identity.getParameter(Constants.Identity.IDENTITY_ID);
            if (!(principal instanceof String) || StringUtils.isBlank((String) principal)
                || "anonymous".equalsIgnoreCase(((String) principal).trim())) {
                throw denied();
            }
            AuthPluginService plugin = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(config.getNacosAuthSystemType()).orElseThrow(this::denied);
            AuthResult<?> decision = plugin.validateAuthority(identity,
                new Permission(resource, action.toString()));
            if (decision == null || !decision.isSuccess()) {
                throw denied();
            }
            return (String) principal;
        } catch (AccessException | RuntimeException unavailable) {
            throw denied();
        }
    }
    
    private NacosApiException denied() {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
            "Hub resource permission denied");
    }
}
