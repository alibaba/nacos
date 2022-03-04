/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.auth.util.Loggers;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;

import java.util.Optional;

/**
 * Abstract protocol auth service.
 *
 * <p>Implement #validateIdentity and #validateAuthority method template.
 *
 * @author xiweng.yy
 */
public abstract class AbstractProtocolAuthService<R> implements ProtocolAuthService<R> {
    
    protected final AuthConfigs authConfigs;
    
    protected AbstractProtocolAuthService(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    @Override
    public boolean enableAuth(Secured secured) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        if (authPluginService.isPresent()) {
            return authPluginService.get().enableAuth(secured.action(), secured.signType());
        }
        Loggers.AUTH.warn("Can't find auth plugin for type {}, please add plugin to classpath or set {} as false",
                authConfigs.getNacosAuthSystemType(), Constants.Auth.NACOS_CORE_AUTH_ENABLED);
        return false;
    }
    
    @Override
    public boolean validateIdentity(IdentityContext identityContext, Resource resource) throws AccessException {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        if (authPluginService.isPresent()) {
            return authPluginService.get().validateIdentity(identityContext, resource);
        }
        return true;
    }
    
    @Override
    public boolean validateAuthority(IdentityContext identityContext, Permission permission) throws AccessException {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        if (authPluginService.isPresent()) {
            return authPluginService.get().validateAuthority(identityContext, permission);
        }
        return true;
    }
    
    /**
     * Get resource from secured annotation specified resource.
     *
     * @param secured secured annotation
     * @return resource
     */
    protected Resource parseSpecifiedResource(Secured secured) {
        return new Resource(null, null, secured.resource(), SignType.SPECIFIED, null);
    }
    
    /**
     * Parse resource by specified resource parser.
     *
     * @param secured secured annotation
     * @param request request
     * @return resource
     */
    protected Resource useSpecifiedParserToParse(Secured secured, R request) {
        try {
            return secured.parser().newInstance().parse(request, secured);
        } catch (Exception e) {
            Loggers.AUTH.error("Use specified resource parser {} parse resource failed.",
                    secured.parser().getCanonicalName(), e);
            return Resource.EMPTY_RESOURCE;
        }
    }
}
