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

import io.grpc.Metadata;

/**
 * Metadata keys used to transport Nacos server identity over JRaft gRPC.
 *
 * @author xiweng.yy
 */
final class JRaftAuthMetadata {
    
    static final Metadata.Key<String> IDENTITY_KEY = Metadata.Key.of(
        "nacos-server-identity-key", Metadata.ASCII_STRING_MARSHALLER);
    
    static final Metadata.Key<String> IDENTITY_VALUE = Metadata.Key.of(
        "nacos-server-identity-value", Metadata.ASCII_STRING_MARSHALLER);
    
    private JRaftAuthMetadata() {
    }
}
