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

package com.alibaba.nacos.ai.remote.manager;

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * The manager of {@code ConnectionBasedClient} For AI module.
 *
 * <p>
 *     proxy for {@link ConnectionBasedClientManager}, only listen connection that module `ai`.
 *     The actual implementation is {@link ConnectionBasedClientManager},
 * </p>
 *
 * @author xiweng.yy
 */
@Service
public class AiConnectionBasedClientManager extends ClientConnectionEventListener implements ClientManager {
    
    private final ConnectionBasedClientManager delegate;
    
    public AiConnectionBasedClientManager(ConnectionBasedClientManager connectionBasedClientManager) {
        this.delegate = connectionBasedClientManager;
    }
    
    @Override
    public void clientConnected(Connection connect) {
        // ignore `naming`, `config` and `lock` module connection
        if (!RemoteConstants.LABEL_MODULE_AI.equals(connect.getMetaInfo().getLabel(RemoteConstants.LABEL_MODULE))) {
            return;
        }
        ClientAttributes attributes = new ClientAttributes();
        attributes.addClientAttribute(ClientConstants.CONNECTION_TYPE, connect.getMetaInfo().getConnectType());
        attributes.addClientAttribute(ClientConstants.CONNECTION_METADATA, connect.getMetaInfo());
        clientConnected(connect.getMetaInfo().getConnectionId(), attributes);
    }
    
    @Override
    public boolean clientConnected(String clientId, ClientAttributes attributes) {
        return delegate.clientConnected(clientId, attributes);
    }
    
    @Override
    public boolean clientConnected(Client client) {
        return delegate.clientConnected(client);
    }
    
    @Override
    public boolean syncClientConnected(String clientId, ClientAttributes attributes) {
        return delegate.syncClientConnected(clientId, attributes);
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        // ignore `naming`, `config` and `lock` module connection
        if (!RemoteConstants.LABEL_MODULE_AI.equals(connect.getMetaInfo().getLabel(RemoteConstants.LABEL_MODULE))) {
            return;
        }
        clientDisconnected(connect.getMetaInfo().getConnectionId());
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        return delegate.clientDisconnected(clientId);
    }
    
    @Override
    public Client getClient(String clientId) {
        return delegate.getClient(clientId);
    }
    
    @Override
    public boolean contains(String clientId) {
        return delegate.contains(clientId);
    }
    
    @Override
    public Collection<String> allClientId() {
        return delegate.allClientId();
    }
    
    @Override
    public boolean isResponsibleClient(Client client) {
        return delegate.isResponsibleClient(client);
    }
    
    @Override
    public boolean verifyClient(DistroClientVerifyInfo verifyData) {
        return delegate.verifyClient(verifyData);
    }
}
