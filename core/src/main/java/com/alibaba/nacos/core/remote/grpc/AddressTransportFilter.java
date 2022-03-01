/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.ServerTransportFilter;

import java.net.InetSocketAddress;

/**
 * AddressTransportFilter process remote address, local address and connection id attributes.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/27 21:23
 */
public class AddressTransportFilter extends ServerTransportFilter {
    
    private final ConnectionManager connectionManager;
    
    public AddressTransportFilter(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    @Override
    public Attributes transportReady(Attributes transportAttrs) {
        InetSocketAddress remoteAddress = (InetSocketAddress) transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        InetSocketAddress localAddress = (InetSocketAddress) transportAttrs.get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
        int remotePort = remoteAddress.getPort();
        int localPort = localAddress.getPort();
        String remoteIp = remoteAddress.getAddress().getHostAddress();
        String connectionId = System.currentTimeMillis() + "_" + remoteIp + "_" + remotePort;
        
        Attributes attrWrapper = transportAttrs.toBuilder().set(GrpcServerConstants.ATTR_TRANS_KEY_CONN_ID, connectionId)
                .set(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_IP, remoteIp)
                .set(GrpcServerConstants.ATTR_TRANS_KEY_REMOTE_PORT, remotePort)
                .set(GrpcServerConstants.ATTR_TRANS_KEY_LOCAL_PORT, localPort).build();
        
        Loggers.REMOTE_DIGEST.info("Connection transportReady,connectionId = {} ", connectionId);
        
        return attrWrapper;
    }
    
    @Override
    public void transportTerminated(Attributes transportAttrs) {
        String connectionId = null;
        try {
            connectionId = transportAttrs.get(GrpcServerConstants.ATTR_TRANS_KEY_CONN_ID);
        } catch (Exception e) {
            // Ignore
        }
        if (StringUtils.isNotBlank(connectionId)) {
            Loggers.REMOTE_DIGEST.info("Connection transportTerminated,connectionId = {} ", connectionId);
            connectionManager.unregister(connectionId);
        }
    }
}
