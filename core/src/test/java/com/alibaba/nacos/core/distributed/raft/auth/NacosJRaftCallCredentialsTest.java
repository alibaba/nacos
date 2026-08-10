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
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosJRaftCallCredentialsTest {
    
    @Test
    void testAppliesCurrentServerIdentity() {
        NacosAuthConfig authConfig = mock(NacosAuthConfig.class);
        when(authConfig.getServerIdentityKey()).thenReturn("identity-key");
        when(authConfig.getServerIdentityValue()).thenReturn("identity-value");
        NacosJRaftCallCredentials credentials = new NacosJRaftCallCredentials(() -> authConfig);
        AtomicReference<Metadata> result = new AtomicReference<>();
        
        credentials.applyRequestMetadata(mock(CallCredentials.RequestInfo.class), Runnable::run,
            new CallCredentials.MetadataApplier() {
                
                @Override
                public void apply(Metadata headers) {
                    result.set(headers);
                }
                
                @Override
                public void fail(Status status) {
                }
            });
        
        assertNotNull(result.get());
        assertEquals("identity-key", result.get().get(JRaftAuthMetadata.IDENTITY_KEY));
        assertEquals("identity-value", result.get().get(JRaftAuthMetadata.IDENTITY_VALUE));
    }
    
    @Test
    void testFailsWhenAuthConfigIsUnavailable() {
        NacosJRaftCallCredentials credentials = new NacosJRaftCallCredentials(() -> null);
        AtomicReference<Status> result = new AtomicReference<>();
        
        credentials.applyRequestMetadata(mock(CallCredentials.RequestInfo.class), Runnable::run,
            new CallCredentials.MetadataApplier() {
                
                @Override
                public void apply(Metadata headers) {
                }
                
                @Override
                public void fail(Status status) {
                    result.set(status);
                }
            });
        
        assertNotNull(result.get());
        assertEquals(Status.Code.UNAUTHENTICATED, result.get().getCode());
    }
}
