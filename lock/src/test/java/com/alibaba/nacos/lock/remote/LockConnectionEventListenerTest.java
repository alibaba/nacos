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

import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LockConnectionEventListenerTest {
    
    @Test
    void testClientConnectedDoesNothing() {
        LockOperationService lockOperationService = mock(LockOperationService.class);
        LockConnectionEventListener listener =
            new LockConnectionEventListener(lockOperationService);
        
        listener.clientConnected(mock(Connection.class));
        
        verifyNoInteractions(lockOperationService);
    }
    
    @Test
    void testClientDisconnectedReleasesLocksByConnection() {
        LockOperationService lockOperationService = mock(LockOperationService.class);
        LockConnectionEventListener listener =
            new LockConnectionEventListener(lockOperationService);
        Connection connection = mockConnection("connection-1");
        
        listener.clientDisConnected(connection);
        
        verify(lockOperationService).releaseLocksByConnection("connection-1");
    }
    
    @Test
    void testClientDisconnectedSwallowsCleanupException() {
        LockOperationService lockOperationService = mock(LockOperationService.class);
        doThrow(new RuntimeException("failed")).when(lockOperationService)
            .releaseLocksByConnection("connection-1");
        LockConnectionEventListener listener =
            new LockConnectionEventListener(lockOperationService);
        
        listener.clientDisConnected(mockConnection("connection-1"));
        
        verify(lockOperationService).releaseLocksByConnection("connection-1");
    }
    
    private Connection mockConnection(String connectionId) {
        ConnectionMeta meta = mock(ConnectionMeta.class);
        when(meta.getConnectionId()).thenReturn(connectionId);
        Connection connection = mock(Connection.class);
        when(connection.getMetaInfo()).thenReturn(meta);
        return connection;
    }
}
