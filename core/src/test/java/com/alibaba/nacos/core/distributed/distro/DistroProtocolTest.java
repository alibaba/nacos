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

package com.alibaba.nacos.core.distributed.distro;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.distributed.distro.task.DistroTaskEngineHolder;
import com.alibaba.nacos.core.distributed.distro.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.core.distributed.distro.task.execute.DistroExecuteTaskExecuteEngine;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link DistroProtocol} unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistroProtocolTest {
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    @Mock
    private DistroTaskEngineHolder distroTaskEngineHolder;
    
    private DistroProtocol distroProtocol;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setIsStandalone(true);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());
        distroProtocol =
            new DistroProtocol(memberManager, distroComponentHolder, distroTaskEngineHolder);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setIsStandalone(null);
    }
    
    @Test
    void testIsInitializedInStandalone() {
        assertTrue(distroProtocol.isInitialized());
    }
    
    @Test
    void testOnQueryWhenStorageNotFound() {
        DistroKey key = new DistroKey("key", "type");
        when(distroComponentHolder.findDataStorage("type")).thenReturn(null);
        
        DistroData result = distroProtocol.onQuery(key);
        
        assertNotNull(result);
        assertEquals(key, result.getDistroKey());
        assertArrayEquals(new byte[0], result.getContent());
    }
    
    @Test
    void testOnQueryWhenStorageFound() {
        DistroKey key = new DistroKey("key", "type");
        byte[] data = new byte[] {1, 2, 3};
        DistroData expected = new DistroData(key, data);
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return true;
            }
            
            @Override
            public DistroData getDistroData(DistroKey key) {
                return expected;
            }
            
            @Override
            public DistroData getDatumSnapshot() {
                return null;
            }
            
            @Override
            public java.util.List<DistroData> getVerifyData() {
                return Collections.emptyList();
            }
        };
        when(distroComponentHolder.findDataStorage("type")).thenReturn(storage);
        
        DistroData result = distroProtocol.onQuery(key);
        
        assertSame(expected, result);
    }
    
    @Test
    void testOnSnapshotWhenStorageNotFound() {
        when(distroComponentHolder.findDataStorage("type")).thenReturn(null);
        
        DistroData result = distroProtocol.onSnapshot("type");
        
        assertNotNull(result);
        assertEquals("snapshot", result.getDistroKey().getResourceKey());
        assertEquals("type", result.getDistroKey().getResourceType());
        assertArrayEquals(new byte[0], result.getContent());
    }
    
    @Test
    void testOnSnapshotWhenStorageFound() {
        DistroKey key = new DistroKey("snapshot", "type");
        DistroData expected = new DistroData(key, new byte[] {1});
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return true;
            }
            
            @Override
            public DistroData getDistroData(DistroKey key) {
                return null;
            }
            
            @Override
            public DistroData getDatumSnapshot() {
                return expected;
            }
            
            @Override
            public java.util.List<DistroData> getVerifyData() {
                return Collections.emptyList();
            }
        };
        when(distroComponentHolder.findDataStorage("type")).thenReturn(storage);
        
        DistroData result = distroProtocol.onSnapshot("type");
        
        assertSame(expected, result);
    }
    
    @Test
    void testOnReceiveWhenProcessorNotFound() {
        DistroKey key = new DistroKey("key", "type");
        DistroData distroData = new DistroData(key, new byte[0]);
        when(distroComponentHolder.findDataProcessor("type")).thenReturn(null);
        
        boolean result = distroProtocol.onReceive(distroData);
        
        assertFalse(result);
    }
    
    @Test
    void testOnReceiveWhenProcessorFound() {
        DistroKey key = new DistroKey("key", "type");
        DistroData distroData = new DistroData(key, new byte[0]);
        DistroDataProcessor processor = new DistroDataProcessor() {
            
            @Override
            public String processType() {
                return "type";
            }
            
            @Override
            public boolean processData(DistroData distroData) {
                return true;
            }
            
            @Override
            public boolean processVerifyData(DistroData distroData, String sourceAddress) {
                return false;
            }
            
            @Override
            public boolean processSnapshot(DistroData distroData) {
                return false;
            }
        };
        when(distroComponentHolder.findDataProcessor("type")).thenReturn(processor);
        
        boolean result = distroProtocol.onReceive(distroData);
        
        assertTrue(result);
    }
    
    @Test
    void testOnVerifyWhenProcessorNotFound() {
        DistroKey key = new DistroKey("key", "type");
        DistroData distroData = new DistroData(key, new byte[0]);
        when(distroComponentHolder.findDataProcessor("type")).thenReturn(null);
        
        boolean result = distroProtocol.onVerify(distroData, "1.1.1.1:8848");
        
        assertFalse(result);
    }
    
    @Test
    void testOnVerifyWhenProcessorFound() {
        DistroKey key = new DistroKey("key", "type");
        DistroData distroData = new DistroData(key, new byte[0]);
        DistroDataProcessor processor = new DistroDataProcessor() {
            
            @Override
            public String processType() {
                return "type";
            }
            
            @Override
            public boolean processData(DistroData distroData) {
                return false;
            }
            
            @Override
            public boolean processVerifyData(DistroData distroData, String sourceAddress) {
                return true;
            }
            
            @Override
            public boolean processSnapshot(DistroData distroData) {
                return false;
            }
        };
        when(distroComponentHolder.findDataProcessor("type")).thenReturn(processor);
        
        boolean result = distroProtocol.onVerify(distroData, "1.1.1.1:8848");
        
        assertTrue(result);
    }
    
    @Test
    void testQueryFromRemoteWhenTargetServerNull() {
        DistroKey key = new DistroKey("key", "type");
        key.setTargetServer(null);
        
        DistroData result = distroProtocol.queryFromRemote(key);
        
        assertNull(result);
    }
    
    @Test
    void testQueryFromRemoteWhenTransportAgentNotFound() {
        DistroKey key = new DistroKey("key", "type", "1.1.1.1:8848");
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(null);
        
        DistroData result = distroProtocol.queryFromRemote(key);
        
        assertNull(result);
    }
    
    @Test
    void testQueryFromRemoteWhenTransportAgentFound() {
        DistroKey key = new DistroKey("key", "type", "1.1.1.1:8848");
        DistroData expected = new DistroData(key, new byte[] {1});
        DistroTransportAgent agent = new DistroTransportAgent() {
            
            @Override
            public boolean supportCallbackTransport() {
                return false;
            }
            
            @Override
            public boolean syncData(DistroData data, String targetServer) {
                return false;
            }
            
            @Override
            public void syncData(DistroData data, String targetServer,
                com.alibaba.nacos.core.distributed.distro.component.DistroCallback callback) {
            }
            
            @Override
            public boolean syncVerifyData(DistroData verifyData, String targetServer) {
                return false;
            }
            
            @Override
            public void syncVerifyData(DistroData verifyData, String targetServer,
                com.alibaba.nacos.core.distributed.distro.component.DistroCallback callback) {
            }
            
            @Override
            public DistroData getData(DistroKey key, String targetServer) {
                return expected;
            }
            
            @Override
            public DistroData getDatumSnapshot(String targetServer) {
                return null;
            }
        };
        when(distroComponentHolder.findTransportAgent("type")).thenReturn(agent);
        
        DistroData result = distroProtocol.queryFromRemote(key);
        
        assertSame(expected, result);
    }
    
    @Test
    void testSyncToTargetAddsTask() {
        DistroTaskEngineHolder realHolder = new DistroTaskEngineHolder(distroComponentHolder);
        DistroProtocol protocol =
            new DistroProtocol(memberManager, distroComponentHolder, realHolder);
        DistroKey key = new DistroKey("res", "type");
        protocol.syncToTarget(key, DataOperation.CHANGE, "2.2.2.2:8848", 100L);
        DistroDelayTaskExecuteEngine engine = realHolder.getDelayTaskExecuteEngine();
        assertNotNull(engine.getProcessor("type"));
    }
    
    @Test
    void testSyncWithMembers() {
        EnvUtil.setEnvironment(new org.springframework.mock.env.MockEnvironment());
        Member member = Member.builder().ip("192.168.1.1").port(8848).build();
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(member));
        DistroTaskEngineHolder realHolder = new DistroTaskEngineHolder(distroComponentHolder);
        DistroProtocol protocol =
            new DistroProtocol(memberManager, distroComponentHolder, realHolder);
        DistroKey key = new DistroKey("res", "type");
        protocol.sync(key, DataOperation.DELETE);
        DistroDelayTaskExecuteEngine engine = realHolder.getDelayTaskExecuteEngine();
        assertNotNull(engine);
    }
    
    @Test
    void testStartDistroTaskWhenNotStandalone() {
        when(distroTaskEngineHolder.getExecuteWorkersManager())
            .thenReturn(new DistroExecuteTaskExecuteEngine());
        try (MockedStatic<GlobalExecutor> globalMock = mockStatic(GlobalExecutor.class)) {
            globalMock.when(() -> GlobalExecutor.submitLoadDataTask(any(Runnable.class)))
                .then(invocation -> null);
            globalMock.when(
                () -> GlobalExecutor.schedulePartitionDataTimedSync(any(Runnable.class), anyLong()))
                .then(invocation -> null);
            EnvUtil.setIsStandalone(false);
            try {
                DistroProtocol protocol = new DistroProtocol(memberManager, distroComponentHolder,
                    distroTaskEngineHolder);
                assertFalse(protocol.isInitialized());
                globalMock.verify(() -> GlobalExecutor.submitLoadDataTask(any(Runnable.class)));
                globalMock.verify(() -> GlobalExecutor
                    .schedulePartitionDataTimedSync(any(Runnable.class), anyLong()));
            } finally {
                EnvUtil.setIsStandalone(null);
            }
        }
    }
}
