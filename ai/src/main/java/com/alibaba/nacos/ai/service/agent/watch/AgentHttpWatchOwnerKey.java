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

package com.alibaba.nacos.ai.service.agent.watch;

import java.util.Objects;

/**
 * Stable admission owner for one HTTP Agent Watch batch stream.
 *
 * @author Nacos
 */
final class AgentHttpWatchOwnerKey {
    
    private final String clientId;
    
    private final String identity;
    
    private final String namespaceId;
    
    AgentHttpWatchOwnerKey(String clientId, String identity, String namespaceId) {
        this.clientId = clientId;
        this.identity = identity;
        this.namespaceId = namespaceId;
    }
    
    String getClientId() {
        return clientId;
    }
    
    String getNamespaceId() {
        return namespaceId;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentHttpWatchOwnerKey)) {
            return false;
        }
        AgentHttpWatchOwnerKey that = (AgentHttpWatchOwnerKey) other;
        return clientId.equals(that.clientId) && identity.equals(that.identity)
            && namespaceId.equals(that.namespaceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(clientId, identity, namespaceId);
    }
}
