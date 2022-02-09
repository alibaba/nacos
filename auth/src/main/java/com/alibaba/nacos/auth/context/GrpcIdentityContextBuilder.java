/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Identity context builder for Grpc.
 *
 * @author Nacos
 */
public class GrpcIdentityContextBuilder implements IdentityContextBuilder<Request> {
    
    private final AuthConfigs authConfigs;
    
    public GrpcIdentityContextBuilder(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    /**
     * get identity context from grpc.
     *
     * @param request grpc request
     * @return IdentityContext request context
     */
    
    @Override
    public IdentityContext build(Request request) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        IdentityContext result = new IdentityContext();
        getRemoteIp(request, result);
        if (!authPluginService.isPresent()) {
            return result;
        }
        Set<String> identityNames = new HashSet<>(authPluginService.get().identityNames());
        Map<String, String> map = request.getHeaders();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (identityNames.contains(entry.getKey())) {
                result.setParameter(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    private void getRemoteIp(Request request, IdentityContext result) {
        result.setParameter(Constants.Identity.REMOTE_IP, request.getHeader(Constants.Identity.X_REAL_IP));
    }
}
