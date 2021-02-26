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

package com.alibaba.nacos.naming.core.v2.client.factory.impl;

import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncAttributes;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactory;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;

/**
 * Client factory for persistent {@link IpPortBasedClient}.
 *
 * @author xiweng.yy
 */
public class PersistentIpPortClientFactory implements ClientFactory<IpPortBasedClient> {
    
    @Override
    public String getType() {
        return ClientConstants.PERSISTENT_IP_PORT;
    }
    
    @Override
    public IpPortBasedClient newClient(String clientId) {
        return new IpPortBasedClient(clientId, false);
    }
    
    @Override
    public IpPortBasedClient newSyncedClient(String clientId, ClientSyncAttributes attributes) {
        return new IpPortBasedClient(clientId, false);
    }
}
