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

import java.util.Properties;

/**
 * Bridges Hub-specific user-operation requirements to existing Nacos auth and visibility SPIs.
 *
 * <p>Hub mutations are fail-closed if authentication or visibility is disabled or missing.
 * This is stricter than ordinary Nacos Skill endpoints because a Hub mutation changes a cross-namespace, publicly
 * discoverable reference. Server-to-server identity alone does not authorize an end-user operation.</p>
 */
@Component
public class HubSkillPermissionGuard {
    
    /**
     * Canonical RBAC resource for administrator takedowns. Assign WRITE to a Hub governance role (or rely on the
     * built-in global administrator). It is not a Namespace permission.
     */
    public static final String HUB_MANAGE_RESOURCE = "ai-hub/manage";
    
    /**
     * Require a readable source Skill for the authenticated caller.
     *
     * @param sourceMeta source Skill metadata from the source namespace
     * @throws NacosException if the caller cannot read the source
     */
    public void requireSourceRead(AiResource sourceMeta) throws NacosException {
        requireSourceVisibility(sourceMeta, VisibilityConstants.ACTION_READ);
    }
    
    /**
     * Require WRITE of a real source Skill, not of the system Hub entry.
     *
     * @param sourceMeta source Skill metadata from the source namespace
     * @throws NacosException if source WRITE permission is absent
     */
    public void requireSourceWrite(AiResource sourceMeta) throws NacosException {
        requireSourceVisibility(sourceMeta, VisibilityConstants.ACTION_WRITE);
    }
    
    /**
     * Require explicit Hub governance WRITE using the active Nacos auth plugin.
     *
     * @throws NacosException if the caller is not a Hub manager
     */
    public void requireHubManage() throws NacosException {
        Caller caller = requireAuthenticatedCaller();
        AuthPluginService authPlugin = AuthPluginManager.getInstance()
            .findAuthServiceSpiImpl(caller.authConfig.getNacosAuthSystemType())
            .orElseThrow(() -> accessDenied("Hub management auth plugin is unavailable"));
        Resource resource =
            new Resource("", "", HUB_MANAGE_RESOURCE, SignType.SPECIFIED, new Properties());
        Permission permission = new Permission(resource, ActionTypes.WRITE.toString());
        try {
            AuthResult<?> result = authPlugin.validateAuthority(caller.identity, permission);
            if (result == null || !result.isSuccess()) {
                throw accessDenied("No Hub management permission");
            }
        } catch (AccessException e) {
            throw accessDenied("No Hub management permission");
        }
    }
    
    private Caller requireAuthenticatedCaller() throws NacosException {
        AuthContext authContext = RequestContextHolder.getContext().getAuthContext();
        String apiType = authContext.getApiType();
        if (!ApiType.ADMIN_API.name().equals(apiType)
            && !ApiType.CONSOLE_API.name().equals(apiType)) {
            throw accessDenied("Hub mutations require an Admin or Console API identity");
        }
        NacosAuthConfig authConfig =
            NacosAuthConfigHolder.getInstance().getNacosAuthConfigByScope(apiType);
        if (authConfig == null || !authConfig.isAuthEnabled()) {
            throw accessDenied("Hub mutations require enabled authentication");
        }
        IdentityContext identity = authContext.getIdentityContext();
        AuthResult<?> authResult = authContext.getAuthResult();
        if (identity == null || authResult == null || !authResult.isSuccess()
            || Boolean.TRUE.equals(
                identity.getParameter(Constants.Identity.SERVER_IDENTITY))) {
            throw accessDenied("Hub mutations require an authenticated user, not server identity");
        }
        Object principal = identity.getParameter(Constants.Identity.IDENTITY_ID);
        if (!(principal instanceof String) || StringUtils.isBlank((String) principal)
            || "anonymous".equalsIgnoreCase(
                (String) principal)) {
            throw accessDenied("Hub mutations require a non-anonymous user");
        }
        return new Caller(identity, authConfig, (String) principal, apiType);
    }
    
    private void requireSourceSkill(AiResource sourceMeta) throws NacosException {
        if (sourceMeta == null || sourceMeta.getId() == null || sourceMeta.getId() <= 0
            || StringUtils.isBlank(
                sourceMeta.getNamespaceId())
            || StringUtils.isBlank(sourceMeta.getName())
            || !AiResourceConstants.RESOURCE_TYPE_SKILL.equals(sourceMeta.getType())
            || HubNamespaceUtils.isHubNamespace(sourceMeta.getNamespaceId())) {
            throw accessDenied("Authorization requires a real source Skill");
        }
    }
    
    private void requireSourceVisibility(AiResource sourceMeta, String action)
        throws NacosException {
        Caller caller = requireAuthenticatedCaller();
        requireSourceSkill(sourceMeta);
        VisibilityService service = VisibilityHelper.findVisibilityService()
            .orElseThrow(() -> accessDenied("Hub mutations require an enabled visibility service"));
        ValidationResult result;
        try {
            result =
                service.validateVisibility(caller.principal, action, caller.apiType, sourceMeta);
        } catch (RuntimeException e) {
            throw accessDenied("Source Skill visibility validation failed");
        }
        if (result == null || !result.isAllowed()) {
            throw accessDenied("No permission for the source Skill");
        }
    }
    
    private NacosApiException accessDenied(String message) {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED, message);
    }
    
    private static final class Caller {
        
        private final IdentityContext identity;
        
        private final NacosAuthConfig authConfig;
        
        private final String principal;
        
        private final String apiType;
        
        private Caller(IdentityContext identity, NacosAuthConfig authConfig, String principal,
            String apiType) {
            this.identity = identity;
            this.authConfig = authConfig;
            this.principal = principal;
            this.apiType = apiType;
        }
    }
}
