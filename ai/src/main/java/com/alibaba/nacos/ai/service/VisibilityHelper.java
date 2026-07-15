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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.ValidationResult;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityPluginManager;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityService;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Helper for visibility checking in AI service layer.
 *
 * @author nacos
 */
public class VisibilityHelper {
    
    private static final String VISIBILITY_PLUGIN_TYPE_CONFIG_KEY = "nacos.plugin.visibility.type";
    
    private static final String DEFAULT_VISIBILITY_SERVICE_NAME = "nacos";
    
    private static volatile String cachedVisibilityServiceName;
    
    private VisibilityHelper() {
    }
    
    /**
     * Resolve the current identity from request context using the plugin-level identity abstraction.
     */
    public static String resolveCurrentIdentity() {
        try {
            IdentityContext identity =
                RequestContextHolder.getContext().getAuthContext().getIdentityContext();
            Object id = identity.getParameter(Constants.Identity.IDENTITY_ID);
            return id == null ? "" : id.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Resolve current API type from auth context.
     *
     * @return api type name, empty string when absent
     */
    public static String resolveCurrentApiType() {
        try {
            String apiType = RequestContextHolder.getContext().getAuthContext().getApiType();
            return apiType == null ? "" : apiType;
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Resolve the client IP from request context.
     *
     * @return client IP address, empty string when absent
     */
    public static String resolveClientIp() {
        try {
            String sourceIp = RequestContextHolder.getContext().getBasicContext()
                .getAddressContext().getSourceIp();
            return sourceIp == null ? "" : sourceIp;
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Filter candidate resources by read permission for current user.
     *
     * @param candidates candidate resources
     * @param <T>        filterable resource type
     * @return resources the current user is allowed to read
     */
    public static <T extends VisibilityResource> List<T> filterReadableResources(
        List<T> candidates) {
        Optional<VisibilityService> visibilityService = findVisibilityService();
        if (visibilityService.isEmpty()) {
            return candidates;
        }
        String currentUser = resolveCurrentIdentity();
        List<T> result = new ArrayList<>(candidates.size());
        for (T each : candidates) {
            ValidationResult validationResult = visibilityService.get()
                .validateVisibility(currentUser, VisibilityConstants.ACTION_READ,
                    resolveCurrentApiType(), each);
            if (validationResult.isAllowed()) {
                result.add(each);
            }
        }
        return result;
    }
    
    /**
     * Check read permission for current user on the given resource.
     *
     * @param resource the resource to check
     * @return true when readable, false otherwise
     */
    public static boolean canReadResource(VisibilityResource resource) {
        Optional<VisibilityService> visibilityService = findVisibilityService();
        if (visibilityService.isEmpty()) {
            return true;
        }
        ValidationResult result = visibilityService.get()
            .validateVisibility(resolveCurrentIdentity(), VisibilityConstants.ACTION_READ,
                resolveCurrentApiType(),
                resource);
        return result.isAllowed();
    }
    
    /**
     * Check write permission for current user on the given resource. Throws 403 if denied.
     *
     * @param resource the resource to check
     * @throws NacosException if no write permission
     */
    public static void checkWritableResource(AiResource resource) throws NacosException {
        Optional<VisibilityService> visibilityService = findVisibilityService();
        if (visibilityService.isEmpty()) {
            return;
        }
        ValidationResult result = visibilityService.get()
            .validateVisibility(resolveCurrentIdentity(), VisibilityConstants.ACTION_WRITE,
                resolveCurrentApiType(),
                resource);
        if (!result.isAllowed()) {
            throw new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
                "No permission to modify " + resource.getType() + ": " + resource.getName());
        }
    }
    
    /**
     * Resolve default scope for creating a new resource, delegated to visibility plugin.
     *
     * @param resourceType resource type, such as skill / agentspec
     * @return resolved default scope, fallback to PRIVATE
     */
    public static String resolveDefaultScopeForCreate(String resourceType) {
        String identity = resolveCurrentIdentity();
        String apiType = resolveCurrentApiType();
        return findVisibilityService()
            .map(service -> service.resolveDefaultScopeForCreate(identity, apiType, resourceType))
            .filter(StringUtils::isNotBlank)
            .map(each -> each.toUpperCase(Locale.ROOT))
            .orElse(VisibilityConstants.SCOPE_PRIVATE);
    }
    
    private static String resolveVisibilityServiceName() {
        String serviceName = cachedVisibilityServiceName;
        if (serviceName != null) {
            return serviceName;
        }
        synchronized (VisibilityHelper.class) {
            if (cachedVisibilityServiceName == null) {
                String configured = EnvUtil.getProperty(VISIBILITY_PLUGIN_TYPE_CONFIG_KEY,
                    DEFAULT_VISIBILITY_SERVICE_NAME);
                cachedVisibilityServiceName = configured.trim();
            }
            return cachedVisibilityServiceName;
        }
    }
    
    /**
     * Find configured visibility service from plugin manager.
     *
     * @return optional visibility service
     */
    public static Optional<VisibilityService> findVisibilityService() {
        return VisibilityPluginManager.getInstance()
            .findVisibilityService(resolveVisibilityServiceName());
    }
}
