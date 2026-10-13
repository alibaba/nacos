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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
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
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

/**
 * Read admission for an explicit, PUBLIC Hub share, separate from source READ.
 *
 * <p>The first version deliberately rejects anonymous users and deployments
 * without enabled authentication/visibility. An authenticated user does NOT
 * need READ permission on the source Namespace to consume an actively shared,
 * fixed PUBLIC version. S2-3's source and publication checks enforce the scope
 * of that grant. This class alone cannot authorize arbitrary source content.</p>
 *
 * <p>HTTP controllers must use Nacos's normal {@code @Secured} authentication
 * so the verified request context is populated. Remote console handlers must
 * preserve end-user identity; server identity alone is never a user grant.</p>
 */
@Component
public class HubSkillReadPermissionGuard {
    
    /**
     * Require a verified end-user request and a configured visibility SPI.
     *
     * <p>Do not consult the source Namespace READ grant here: that would defeat
     * the dedicated Hub sharing semantics. Callers must also verify the active
     * publication and live source before accessing any data.</p>
     *
     * @throws NacosException when Hub read admission is not possible
     */
    public void requireHubRead() throws NacosException {
        requireCaller();
        try {
            Optional<VisibilityService> service = VisibilityHelper.findVisibilityService();
            if (service == null || service.isEmpty()) {
                throw denied();
            }
        } catch (RuntimeException failedVisibilityLookup) {
            throw denied();
        }
    }
    
    /**
     * Optional source deep-link permission, never a prerequisite for Hub read.
     *
     * @param source a freshly revalidated real source Skill, not the Hub row
     * @return true only if ordinary source READ authorization and visibility both allow it
     * @throws NacosException if the caller is not authenticated
     */
    public boolean canReadSource(AiResource source) throws NacosException {
        Caller caller = requireCaller();
        if (!isRealSource(source)) {
            return false;
        }
        try {
            Optional<VisibilityService> available = VisibilityHelper.findVisibilityService();
            if (available == null || available.isEmpty()) {
                return false;
            }
            Optional<AuthPluginService> authService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(caller.authSystemType);
            if (authService == null || authService.isEmpty()) {
                return false;
            }
            Properties properties = new Properties();
            properties.setProperty(Constants.Resource.AI_TYPE, Constants.Resource.AI_TYPE_SKILL);
            properties.setProperty(Constants.Resource.ACTION, ActionTypes.READ.toString());
            Resource resource = new Resource(source.getNamespaceId(),
                com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP, source.getName(),
                SignType.AI, properties);
            AuthResult<?> authority = authService.get()
                .validateAuthority(caller.identity,
                    new Permission(resource, ActionTypes.READ.toString()));
            if (authority == null || !authority.isSuccess()) {
                return false;
            }
            ValidationResult decision = available.get().validateVisibility(caller.principal,
                VisibilityConstants.ACTION_READ, caller.apiType, source);
            return decision != null && decision.isAllowed();
        } catch (AccessException | RuntimeException missingOrFailedPlugin) {
            return false;
        }
    }
    
    private boolean isRealSource(AiResource source) {
        return source != null && source.getId() != null && source.getId() > 0
            && StringUtils.isNotBlank(source.getNamespaceId())
            && !HubNamespaceUtils.isHubNamespace(source.getNamespaceId())
            && StringUtils.isNotBlank(source.getName())
            && AiResourceConstants.RESOURCE_TYPE_SKILL.equals(source.getType());
    }
    
    private Caller requireCaller() throws NacosException {
        try {
            AuthContext auth = RequestContextHolder.getContext().getAuthContext();
            if (auth == null) {
                throw denied();
            }
            String apiType = auth.getApiType();
            if (!ApiType.CONSOLE_API.name().equals(apiType)
                && !ApiType.ADMIN_API.name().equals(apiType)
                && !ApiType.OPEN_API.name().equals(apiType)) {
                throw denied();
            }
            NacosAuthConfig config = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(apiType);
            if (config == null || !config.isAuthEnabled()) {
                throw denied();
            }
            IdentityContext identity = auth.getIdentityContext();
            AuthResult<?> result = auth.getAuthResult();
            if (identity == null || result == null || !result.isSuccess()
                || isServerIdentity(identity.getParameter(Constants.Identity.SERVER_IDENTITY))) {
                throw denied();
            }
            Object identityId = identity.getParameter(Constants.Identity.IDENTITY_ID);
            if (!(identityId instanceof String) || StringUtils.isBlank((String) identityId)
                || "anonymous".equalsIgnoreCase(((String) identityId).trim())) {
                throw denied();
            }
            return new Caller((String) identityId, apiType, identity,
                config.getNacosAuthSystemType());
        } catch (NacosApiException e) {
            throw e;
        } catch (RuntimeException invalidContext) {
            throw denied();
        }
    }
    
    private boolean isServerIdentity(Object value) {
        return Boolean.TRUE.equals(value)
            || (value instanceof String && "true".equalsIgnoreCase(((String) value).trim()));
    }
    
    private NacosApiException denied() {
        return new NacosApiException(NacosException.NO_RIGHT,
            ErrorCode.ACCESS_DENIED, "Hub Skill read access denied");
    }
    
    private static final class Caller {
        
        private final String principal;
        
        private final String apiType;
        
        private final IdentityContext identity;
        
        private final String authSystemType;
        
        private Caller(String principal, String apiType, IdentityContext identity,
            String authSystemType) {
            this.principal = principal;
            this.apiType = apiType;
            this.identity = identity;
            this.authSystemType = authSystemType;
        }
    }
}
