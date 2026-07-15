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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.core.remote.ConnectionManager;
import io.grpc.Attributes;
import io.grpc.Grpc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;

import static com.alibaba.nacos.core.remote.grpc.GrpcServerConstants.ATTR_TRANS_KEY_CONN_ID;
import static com.alibaba.nacos.core.remote.grpc.GrpcServerConstants.ATTR_TRANS_KEY_LOCAL_PORT;
import static com.alibaba.nacos.core.remote.grpc.GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_IP;
import static com.alibaba.nacos.core.remote.grpc.GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddressTransportFilterTest {
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Test
    void testTransportReady() {
        AddressTransportFilter filter = new AddressTransportFilter(connectionManager);
        InetSocketAddress remote = new InetSocketAddress("192.168.1.1", 12345);
        InetSocketAddress local = new InetSocketAddress("0.0.0.0", 9848);
        Attributes transportAttrs = Attributes.newBuilder()
            .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, remote)
            .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, local)
            .build();
        Attributes result = filter.transportReady(transportAttrs);
        assertNotNull(result);
        assertNotNull(result.get(ATTR_TRANS_KEY_CONN_ID));
        assertEquals("192.168.1.1", result.get(ATTR_TRANS_KEY_REMOTE_IP));
        assertEquals(12345, (int) result.get(ATTR_TRANS_KEY_REMOTE_PORT));
        assertEquals(9848, (int) result.get(ATTR_TRANS_KEY_LOCAL_PORT));
    }
    
    @Test
    void testTransportTerminatedWithConnectionId() {
        AddressTransportFilter filter = new AddressTransportFilter(connectionManager);
        Attributes transportAttrs = Attributes.newBuilder()
            .set(ATTR_TRANS_KEY_CONN_ID, "conn-123")
            .build();
        filter.transportTerminated(transportAttrs);
        verify(connectionManager).unregister("conn-123");
    }
    
    @Test
    void testTransportTerminatedWithBlankConnectionId() {
        AddressTransportFilter filter = new AddressTransportFilter(connectionManager);
        Attributes transportAttrs = Attributes.newBuilder()
            .set(ATTR_TRANS_KEY_CONN_ID, "   ")
            .build();
        filter.transportTerminated(transportAttrs);
    }
}
