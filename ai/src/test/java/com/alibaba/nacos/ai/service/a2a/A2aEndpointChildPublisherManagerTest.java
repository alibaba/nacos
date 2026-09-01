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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.remote.manager.AiConnectionBasedClientManager;
import com.alibaba.nacos.ai.service.a2a.A2aEndpointChildPublisherManager.ChildPublisher;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aEndpointChildPublisherManagerTest {
    
    private static final String PARENT = "parent";
    
    @Mock
    private AiConnectionBasedClientManager clientManager;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta meta;
    
    private A2aEndpointChildPublisherManager manager;
    
    private Set<String> connected;
    
    @BeforeEach
    void setUp() {
        manager = new A2aEndpointChildPublisherManager(clientManager);
        connected = new HashSet<String>();
        connected.add(PARENT);
        when(clientManager.contains(anyString()))
            .thenAnswer(invocation -> connected.contains(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> connected.add(invocation.getArgument(0)))
            .when(clientManager).clientConnected(anyString(), any(ClientAttributes.class));
        lenient().doAnswer(invocation -> connected.remove(invocation.getArgument(0)))
            .when(clientManager).clientDisconnected(anyString());
    }
    
    @Test
    void shouldCreateReuseAndSeparateDeterministicChildren() {
        ChildPublisher first = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "legacy");
        ChildPublisher reused = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "legacy");
        ChildPublisher canonical = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "canonical");
        ChildPublisher version = manager.ensureChild(PARENT, "public", "agent", "2.0.0",
            "legacy");
        
        assertTrue(first.isCreated());
        assertFalse(reused.isCreated());
        assertEquals(first.getClientId(), reused.getClientId());
        assertNotEquals(first.getClientId(), canonical.getClientId());
        assertNotEquals(first.getClientId(), version.getClientId());
        assertTrue(first.getClientId().startsWith(
            A2aEndpointChildPublisherManager.CHILD_CLIENT_ID_PREFIX));
        assertEquals(3, manager.childCount(PARENT));
        assertEquals(first.getClientId(), manager.findChild(PARENT, "public", "agent", "1.0.0",
            "legacy"));
        
        manager.disconnectChild(PARENT, first.getClientId());
        assertEquals(2, manager.childCount(PARENT));
        assertNull(manager.findChild(PARENT, "public", "agent", "1.0.0", "legacy"));
    }
    
    @Test
    void shouldValidateParentLayoutAndChildCreation() {
        assertThrows(IllegalArgumentException.class,
            () -> manager.ensureChild(PARENT, "public", "agent", "1.0.0", " "));
        
        connected.remove(PARENT);
        NacosRuntimeException disconnected = assertThrows(NacosRuntimeException.class,
            () -> manager.ensureChild(PARENT, "public", "agent", "1.0.0", "legacy"));
        assertEquals(NacosException.CLIENT_DISCONNECT, disconnected.getErrCode());
        
        connected.add(PARENT);
        when(clientManager.clientConnected(anyString(), any(ClientAttributes.class)))
            .thenReturn(false);
        NacosRuntimeException failed = assertThrows(NacosRuntimeException.class,
            () -> manager.ensureChild(PARENT, "public", "agent", "1.0.0", "legacy"));
        assertEquals(NacosException.SERVER_ERROR, failed.getErrCode());
    }
    
    @Test
    void shouldAcceptConcurrentCreatorResult() {
        when(clientManager.clientConnected(anyString(), any(ClientAttributes.class)))
            .thenAnswer(invocation -> {
                connected.add(invocation.getArgument(0));
                return false;
            });
        
        ChildPublisher result = manager.ensureChild(PARENT, null, "a:b", "1", "legacy");
        
        assertFalse(result.isCreated());
        assertEquals(1, manager.childCount(PARENT));
    }
    
    @Test
    void shouldCleanNewChildWhenParentDisconnectsDuringCreation() {
        doAnswer(invocation -> {
            connected.add(invocation.getArgument(0));
            connected.remove(PARENT);
            return true;
        }).when(clientManager).clientConnected(anyString(), any(ClientAttributes.class));
        
        NacosRuntimeException exception = assertThrows(NacosRuntimeException.class,
            () -> manager.ensureChild(PARENT, "public", "agent", "1.0.0", "legacy"));
        
        assertEquals(NacosException.CLIENT_DISCONNECT, exception.getErrCode());
        verify(clientManager).clientDisconnected(anyString());
        assertEquals(0, manager.childCount(PARENT));
    }
    
    @Test
    void shouldReleaseOnlyChildrenOfAiConnection() {
        ChildPublisher first = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "legacy");
        ChildPublisher second = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "canonical");
        when(connection.getMetaInfo()).thenReturn(meta);
        when(meta.getConnectionId()).thenReturn(PARENT);
        when(meta.getLabel(RemoteConstants.LABEL_MODULE)).thenReturn("naming");
        
        manager.clientConnected(connection);
        manager.clientDisConnected(connection);
        assertEquals(2, manager.childCount(PARENT));
        
        when(meta.getLabel(RemoteConstants.LABEL_MODULE))
            .thenReturn(RemoteConstants.LABEL_MODULE_AI);
        manager.clientDisConnected(connection);
        assertEquals(0, manager.childCount(PARENT));
        verify(clientManager).clientDisconnected(first.getClientId());
        verify(clientManager).clientDisconnected(second.getClientId());
        
        manager.clientDisConnected(connection);
    }
    
    @Test
    void shouldRemoveStaleTrackedChildWhenLookupMisses() {
        ChildPublisher child = manager.ensureChild(PARENT, "public", "agent", "1.0.0",
            "legacy");
        connected.remove(child.getClientId());
        
        assertNull(manager.findChild(PARENT, "public", "agent", "1.0.0", "legacy"));
        assertEquals(0, manager.childCount(PARENT));
        manager.disconnectChild(PARENT, child.getClientId());
        verify(clientManager).clientDisconnected(child.getClientId());
    }
}
