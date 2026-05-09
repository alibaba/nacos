/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class ConnectionGeneratorServiceDelegateTest {
    
    @Mock
    private StreamObserver<Object> streamObserver;
    
    @Mock
    private Channel channel;
    
    @Test
    void testGetInstance() {
        ConnectionGeneratorServiceDelegate delegate =
            ConnectionGeneratorServiceDelegate.getInstance();
        assertNotNull(delegate);
    }
    
    @Test
    void testGetConnection() {
        ConnectionGeneratorServiceDelegate delegate =
            ConnectionGeneratorServiceDelegate.getInstance();
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", Collections.emptyMap());
        Connection conn = delegate.getConnection(meta, streamObserver, channel);
        assertNotNull(conn);
        assertNotNull(conn.getMetaInfo());
    }
}
