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

package com.alibaba.nacos.naming.core.v2.client.manager;

import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncAttributes;

import java.util.Collection;

/**
 * The manager of {@code Client} Nacos naming client.
 *
 * @author xiweng.yy
 */
public interface ClientManager {
    
    /**
     * New client connected.
     *
     * @param client new client
     * @return true if add successfully, otherwise false
     */
    boolean clientConnected(Client client);
    
    /**
     * New sync client connected.
     *
     * @param clientId   synced client id
     * @param attributes client sync attributes, which can help create sync client
     * @return true if add successfully, otherwise false
     */
    boolean syncClientConnected(String clientId, ClientSyncAttributes attributes);
    
    /**
     * Client disconnected.
     *
     * @param clientId client id
     * @return true if remove successfully, otherwise false
     */
    boolean clientDisconnected(String clientId);
    
    /**
     * Get client by id.
     *
     * @param clientId client id
     * @return client
     */
    Client getClient(String clientId);
    
    /**
     * Whether the client id exists.
     *
     * @param clientId client id
     * @return client
     */
    boolean contains(final String clientId);
    
    /**
     * All client id.
     *
     * @return collection of client id
     */
    Collection<String> allClientId();
    
    /**
     * Whether the client is responsible by current server.
     *
     * @param client client
     * @return true if responsible, otherwise false
     */
    boolean isResponsibleClient(Client client);
    
    /**
     * verify client.
     *
     * @param clientId client id
     * @return true if client is valid, otherwise is false.
     */
    boolean verifyClient(String clientId);
}
