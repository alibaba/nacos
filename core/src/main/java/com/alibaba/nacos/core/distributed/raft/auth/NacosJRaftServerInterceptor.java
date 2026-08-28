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

package com.alibaba.nacos.core.distributed.raft.auth;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.auth.serveridentity.ServerIdentity;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityChecker;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityCheckerHolder;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Validates Nacos server identity before dispatching a native JRaft gRPC request.
 *
 * @author xiweng.yy
 */
@Secured(resource = "jraft", signType = SignType.SPECIFIED, apiType = ApiType.INNER_API)
public class NacosJRaftServerInterceptor implements ServerInterceptor {
    
    private static final Secured JRAFT_SECURED =
        NacosJRaftServerInterceptor.class.getAnnotation(Secured.class);
    
    private final JRaftAuthUpgradeCoordinator upgradeCoordinator;
    
    private final NacosAuthConfig authConfig;
    
    private final ServerIdentityChecker identityChecker;
    
    public NacosJRaftServerInterceptor(JRaftAuthUpgradeCoordinator upgradeCoordinator) {
        this(upgradeCoordinator, NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE),
            ServerIdentityCheckerHolder.getInstance().newChecker());
    }
    
    NacosJRaftServerInterceptor(JRaftAuthUpgradeCoordinator upgradeCoordinator,
        NacosAuthConfig authConfig, ServerIdentityChecker identityChecker) {
        this.upgradeCoordinator = upgradeCoordinator;
        this.authConfig = authConfig;
        this.identityChecker = identityChecker;
        this.identityChecker.init(authConfig);
    }
    
    @Override
    public <T, R> ServerCall.Listener<T> interceptCall(
        ServerCall<T, R> call, Metadata headers,
        ServerCallHandler<T, R> next) {
        if (isCredentialValid(headers) || upgradeCoordinator.allowInvalidCredential()) {
            return next.startCall(call, headers);
        }
        call.close(Status.UNAUTHENTICATED.withDescription("Nacos server identity not matched"),
            new Metadata());
        return new ServerCall.Listener<T>() {
        };
    }
    
    private boolean isCredentialValid(Metadata headers) {
        if (authConfig == null || StringUtils.isBlank(authConfig.getServerIdentityKey())
            || StringUtils.isBlank(authConfig.getServerIdentityValue())) {
            return false;
        }
        String identityKey = headers.get(JRaftAuthMetadata.IDENTITY_KEY);
        String identityValue = headers.get(JRaftAuthMetadata.IDENTITY_VALUE);
        if (!authConfig.getServerIdentityKey().equals(identityKey)) {
            return false;
        }
        try {
            ServerIdentityResult result = identityChecker.check(
                new ServerIdentity(identityKey, identityValue), JRAFT_SECURED);
            return ServerIdentityResult.ResultStatus.MATCHED == result.getStatus();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
