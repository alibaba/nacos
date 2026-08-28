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

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.core.auth.NacosServerAuthConfig;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Supplies the configured Nacos server identity to every JRaft gRPC request.
 *
 * @author xiweng.yy
 */
public class NacosJRaftCallCredentials extends CallCredentials {
    
    private final Supplier<NacosAuthConfig> authConfigSupplier;
    
    public NacosJRaftCallCredentials() {
        this(() -> NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE));
    }
    
    NacosJRaftCallCredentials(Supplier<NacosAuthConfig> authConfigSupplier) {
        this.authConfigSupplier = authConfigSupplier;
    }
    
    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor,
        MetadataApplier applier) {
        appExecutor.execute(() -> {
            try {
                NacosAuthConfig authConfig = Objects.requireNonNull(authConfigSupplier.get(),
                    "Nacos server auth config");
                Metadata metadata = new Metadata();
                metadata.put(JRaftAuthMetadata.IDENTITY_KEY,
                    Objects.toString(authConfig.getServerIdentityKey(), ""));
                metadata.put(JRaftAuthMetadata.IDENTITY_VALUE,
                    Objects.toString(authConfig.getServerIdentityValue(), ""));
                applier.apply(metadata);
            } catch (Exception e) {
                applier.fail(Status.UNAUTHENTICATED.withDescription(
                    "Failed to obtain Nacos server identity").withCause(e));
            }
        });
    }
    
    @Override
    public void thisUsesUnstableApi() {
        // Required by the gRPC CallCredentials contract.
    }
}
