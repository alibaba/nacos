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
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.api.common.ApiType;
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
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.common.utils.StringUtils;
import java.util.Optional;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Target WRITE is an explicit AI auth-plugin authority check against the proposed
 * new target name. A nonexistent target cannot be authorized by its ownership.
 * Direct copy additionally needs independent source READ; Hub import does not.
 */
@Component
public class HubSkillTransferPermissionGuard {
    
    private final HubSkillReadPermissionGuard hubRead;
    private final HubSkillPermissionGuard sourcePermission;
    
    public HubSkillTransferPermissionGuard(HubSkillReadPermissionGuard hubRead,
        HubSkillPermissionGuard sourcePermission) {
        this.hubRead = hubRead;
        this.sourcePermission = sourcePermission;
    }
    
    /** Require verified user identity, enabled auth and explicit target AI WRITE. */
    public void requireTargetWrite(String namespaceId, String targetName) throws NacosException {
        requireAuthority(namespaceId, targetName, ActionTypes.WRITE);
    }
    
    private void requireAuthority(String namespaceId, String name, ActionTypes action)
        throws NacosException {
        // Reuse S2 strict admission. This rejects anonymous/server-only and disabled auth.
        hubRead.requireHubRead();
        try {
            AuthContext context = RequestContextHolder.getContext().getAuthContext();
            if (!ApiType.ADMIN_API.name().equals(context.getApiType())
                && !ApiType.CONSOLE_API.name().equals(context.getApiType())) {
                throw denied();
            }
            NacosAuthConfig config = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(context.getApiType());
            if (config == null || !config.isAuthEnabled()
                || StringUtils.isBlank(config.getNacosAuthSystemType())) {
                throw denied();
            }
            IdentityContext identity = context.getIdentityContext();
            AuthResult<?> authentication = context.getAuthResult();
            if (identity == null || authentication == null || !authentication.isSuccess()
                || Boolean.parseBoolean(
                    String.valueOf(identity.getParameter(Constants.Identity.SERVER_IDENTITY)))) {
                throw denied();
            }
            Object principal = identity.getParameter(Constants.Identity.IDENTITY_ID);
            if (!(principal instanceof String) || StringUtils.isBlank((String) principal)
                || "anonymous".equalsIgnoreCase((String) principal)) {
                throw denied();
            }
            Optional<AuthPluginService> plugin = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(config.getNacosAuthSystemType());
            if (plugin.isEmpty()) {
                throw denied();
            }
            Properties properties = new Properties();
            properties.setProperty(Constants.Resource.AI_TYPE, Constants.Resource.AI_TYPE_SKILL);
            properties.setProperty(Constants.Resource.ACTION, action.toString());
            Resource resource = new Resource(namespaceId,
                com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, name,
                SignType.AI, properties);
            AuthResult<?> result = plugin.get().validateAuthority(identity,
                new Permission(resource, action.toString()));
            if (result == null || !result.isSuccess()) {
                throw denied();
            }
        } catch (NacosApiException error) {
            throw error;
        } catch (AccessException | RuntimeException pluginFailure) {
            throw denied();
        }
    }
    
    /** Direct source copies must pass ordinary source READ, unlike Hub imports. */
    public void requireDirectSourceRead(AiResource actualSource) throws NacosException {
        if (actualSource == null || actualSource.getId() == null || actualSource.getId() <= 0
            || StringUtils.isBlank(actualSource.getNamespaceId())
            || StringUtils.isBlank(actualSource.getName())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(actualSource.getType())
            || HubNamespaceUtils.isHubNamespace(actualSource.getNamespaceId())) {
            throw denied();
        }
        requireAuthority(actualSource.getNamespaceId(), actualSource.getName(), ActionTypes.READ);
        sourcePermission.requireSourceRead(actualSource);
    }
    
    private NacosApiException denied() {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
            "Hub Skill transfer permission denied");
    }
}
