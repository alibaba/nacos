/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.event.client;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.client.Client;

/**
 * Client event.
 *
 * @author xiweng.yy
 */
public class ClientEvent extends Event {
    
    private static final long serialVersionUID = -8211818115593181708L;
    
    private final Client client;
    
    public ClientEvent(Client client) {
        this.client = client;
    }
    
    public Client getClient() {
        return client;
    }
    
    /**
     * Client changed event. Happened when {@code Client} add or remove service.
     */
    public static class ClientChangedEvent extends ClientEvent {
        
        private static final long serialVersionUID = 6440402443724824673L;
        
        public ClientChangedEvent(Client client) {
            super(client);
        }
        
    }
    
    /**
     * Client disconnect event. Happened when {@code Client} disconnect with server.
     */
    public static class ClientDisconnectEvent extends ClientEvent {
        
        private static final long serialVersionUID = 370348024867174101L;
        
        public ClientDisconnectEvent(Client client) {
            super(client);
        }
        
    }
    
    /**
     * Client add event. Happened when verify failed.
     */
    public static class ClientVerifyFailedEvent extends ClientEvent {
    
        private static final long serialVersionUID = 2023951686223780851L;
    
        private final String clientId;
        
        private final String targetServer;
        
        public ClientVerifyFailedEvent(String clientId, String targetServer) {
            super(null);
            this.clientId = clientId;
            this.targetServer = targetServer;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public String getTargetServer() {
            return targetServer;
        }
    }
}
