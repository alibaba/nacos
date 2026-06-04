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

package com.alibaba.nacos.lock.remote;

import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens for client connection events to clean up lock state on disconnect.
 *
 * <p>When a client disconnects, any locks held by that connection are force-released
 * and waiting entries from that connection are removed from wait queues.
 *
 * @author DHX
 * @date 2026/05/29
 */
@Component
public class LockConnectionEventListener extends ClientConnectionEventListener {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LockConnectionEventListener.class);
    
    private final LockOperationService lockOperationService;
    
    public LockConnectionEventListener(LockOperationService lockOperationService) {
        this.lockOperationService = lockOperationService;
        setName("LockConnectionEventListener");
    }
    
    @Override
    public void clientConnected(Connection connect) {
    }
    
    @Override
    public void clientDisConnected(Connection connect) {
        String connectionId = connect.getMetaInfo().getConnectionId();
        LOGGER.info("Lock: client disconnected, connectionId={}, cleaning up locks", connectionId);
        try {
            lockOperationService.releaseLocksByConnection(connectionId);
        } catch (Exception e) {
            LOGGER.error("Lock: failed to clean up locks for disconnected connectionId={}",
                connectionId, e);
        }
    }
}
