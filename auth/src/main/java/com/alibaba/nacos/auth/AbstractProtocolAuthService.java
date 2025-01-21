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
import com.alibaba.nacos.auth.serveridentity.ServerIdentity;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityChecker;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityCheckerHolder;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.auth.util.Loggers;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;

import java.util.Optional;
import java.util.Properties;

/**
 * Abstract protocol auth service.
 *
 * <p>Implement #validateIdentity and #validateAuthority method template.
 *
 * @author xiweng.yy
 */
public abstract class AbstractProtocolAuthService<R> implements ProtocolAuthService<R> {
    
    protected final AuthConfigs authConfigs;
    
    protected final ServerIdentityChecker checker;
    
    protected AbstractProtocolAuthService(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
        this.checker = ServerIdentityCheckerHolder.getInstance().getChecker();
    }
    
    @Override
    public void initialize() {
        this.checker.init(authConfigs);
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
    
    @Override
    public ServerIdentityResult checkServerIdentity(R request, Secured secured) {
        if (isInvalidServerIdentity()) {
            return ServerIdentityResult.fail(
                    "Invalid server identity key or value, Please make sure set `nacos.core.auth.server.identity.key`"
                            + " and `nacos.core.auth.server.identity.value`, or open `nacos.core.auth.enable.userAgentAuthWhite`");
        }
        ServerIdentity serverIdentity = parseServerIdentity(request);
        return checker.check(serverIdentity, secured);
    }
    
    private boolean isInvalidServerIdentity() {
        return StringUtils.isBlank(authConfigs.getServerIdentityKey()) || StringUtils.isBlank(
                authConfigs.getServerIdentityValue());
    }
    
    /**
     * Parse server identity from protocol request.
     *
     * @param request protocol request
     * @return nacos server identity.
     */
    protected abstract ServerIdentity parseServerIdentity(R request);
    
    /**
     * Get resource from secured annotation specified resource.
     *
     * @param secured secured annotation
     * @return resource
     */
    protected Resource parseSpecifiedResource(Secured secured) {
        Properties properties = new Properties();
        for (String each : secured.tags()) {
            properties.put(each, each);
        }
        return new Resource(null, null, secured.resource(), SignType.SPECIFIED, properties);
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
