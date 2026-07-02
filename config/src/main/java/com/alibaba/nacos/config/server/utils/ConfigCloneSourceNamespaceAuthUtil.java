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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

/**
 * Temporary auth util for checking clone source namespace read permission.
 *
 * @author xiweng.yy
 */
@Component
public class ConfigCloneSourceNamespaceAuthUtil {

    private final AuthConfigs authConfigs;

    public ConfigCloneSourceNamespaceAuthUtil(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }

    /**
     * Check READ permission of source namespace for clone config request.
     *
     * @param sourceTenant processed source tenant
     * @throws AccessException if auth failed
     */
    public void checkReadPermission(String sourceTenant) throws AccessException {
        if (!authConfigs.isAuthEnabled()) {
            return;
        }
        IdentityContext identityContext = RequestContextHolder.getContext().getAuthContext().getIdentityContext();
        if (identityContext == null) {
            return;
        }
        Optional<AuthPluginService> authPluginService = findAuthPluginService();
        if (!authPluginService.isPresent()
                || !authPluginService.get().enableAuth(ActionTypes.READ, SignType.CONFIG)) {
            return;
        }
        Resource sourceResource = buildSourceNamespaceReadResource(sourceTenant);
        if (!validateAuthority(authPluginService.get(), identityContext, sourceResource)) {
            LogUtil.DEFAULT_LOG.warn(
                    "[ConfigCloneSourceNamespaceAuthUtil] Clone source namespace read permission denied, sourceTenant={}, authSystemType={}",
                    sourceResource.getNamespaceId(), authConfigs.getNacosAuthSystemType());
            throw newAccessException("Validate Authority failed.");
        }
    }

    Optional<AuthPluginService> findAuthPluginService() {
        return AuthPluginManager.getInstance().findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
    }

    private Resource buildSourceNamespaceReadResource(String sourceTenant) {
        Properties properties = new Properties();
        properties.put(Constants.Resource.ACTION, ActionTypes.READ.toString());
        String namespaceId = StringUtils.isBlank(sourceTenant) ? StringUtils.EMPTY : sourceTenant;
        return new Resource(namespaceId, StringUtils.EMPTY, StringUtils.EMPTY, SignType.CONFIG, properties);
    }

    private boolean validateAuthority(AuthPluginService authPluginService, IdentityContext identityContext,
            Resource sourceResource) throws AccessException {
        try {
            return Boolean.TRUE.equals(authPluginService.validateAuthority(identityContext,
                    new Permission(sourceResource, ActionTypes.READ.toString())));
        } catch (AccessException e) {
            throw newAccessException(e.getErrMsg());
        }
    }

    private static AccessException newAccessException(String message) {
        AccessException exception = new AccessException(NacosException.NO_RIGHT);
        exception.setErrMsg(message);
        return exception;
    }
}
