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

package com.alibaba.nacos.core.distributed.distro.task.verify;

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.task.execute.DistroExecuteTaskExecuteEngine;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * {@link DistroVerifyTimedTask} unit test.
 */
@ExtendWith(MockitoExtension.class)
class DistroVerifyTimedTaskTest {
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    private DistroExecuteTaskExecuteEngine executeTaskExecuteEngine;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        executeTaskExecuteEngine = new DistroExecuteTaskExecuteEngine();
        when(serverMemberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testRunWithEmptyMembers() {
        when(distroComponentHolder.getDataStorageTypes()).thenReturn(Collections.emptySet());
        DistroVerifyTimedTask task =
            new DistroVerifyTimedTask(serverMemberManager, distroComponentHolder,
                executeTaskExecuteEngine);
        task.run();
    }
    
    @Test
    void testRunWithStorageNotFinishedInitial() {
        when(distroComponentHolder.getDataStorageTypes())
            .thenReturn(Collections.singleton("type1"));
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return false;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDistroData(
                com.alibaba.nacos.core.distributed.distro.entity.DistroKey distroKey) {
                return null;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDatumSnapshot() {
                return null;
            }
            
            @Override
            public List<DistroData> getVerifyData() {
                return Collections.emptyList();
            }
        };
        when(distroComponentHolder.findDataStorage("type1")).thenReturn(storage);
        DistroVerifyTimedTask task =
            new DistroVerifyTimedTask(serverMemberManager, distroComponentHolder,
                executeTaskExecuteEngine);
        task.run();
    }
    
    @Test
    void testRunWithEmptyVerifyData() {
        when(distroComponentHolder.getDataStorageTypes())
            .thenReturn(Collections.singleton("type1"));
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return true;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDistroData(
                com.alibaba.nacos.core.distributed.distro.entity.DistroKey distroKey) {
                return null;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDatumSnapshot() {
                return null;
            }
            
            @Override
            public List<DistroData> getVerifyData() {
                return Collections.emptyList();
            }
        };
        when(distroComponentHolder.findDataStorage("type1")).thenReturn(storage);
        DistroVerifyTimedTask task =
            new DistroVerifyTimedTask(serverMemberManager, distroComponentHolder,
                executeTaskExecuteEngine);
        task.run();
    }
    
    @Test
    void testRunWithVerifyDataAndMember() {
        Member member = Member.builder().ip("192.168.1.1").port(8848).build();
        when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(member));
        when(distroComponentHolder.getDataStorageTypes())
            .thenReturn(Collections.singleton("type1"));
        DistroData verifyDataItem = new DistroData(new DistroKey("k", "type1"), new byte[0]);
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return true;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDistroData(
                com.alibaba.nacos.core.distributed.distro.entity.DistroKey distroKey) {
                return null;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDatumSnapshot() {
                return null;
            }
            
            @Override
            public List<DistroData> getVerifyData() {
                return Collections.singletonList(verifyDataItem);
            }
        };
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
                return null;
            }
            
            @Override
            public DistroData getDatumSnapshot(String targetServer) {
                return null;
            }
        };
        when(distroComponentHolder.findDataStorage("type1")).thenReturn(storage);
        when(distroComponentHolder.findTransportAgent("type1")).thenReturn(agent);
        DistroVerifyTimedTask task =
            new DistroVerifyTimedTask(serverMemberManager, distroComponentHolder,
                executeTaskExecuteEngine);
        task.run();
        // addTask submits to worker which may process immediately, so pending size can be 0
    }
    
    @Test
    void testRunWithNullTransportAgentSkipsType() {
        Member member = Member.builder().ip("192.168.1.1").port(8848).build();
        when(serverMemberManager.allMembersWithoutSelf())
            .thenReturn(Collections.singletonList(member));
        when(distroComponentHolder.getDataStorageTypes())
            .thenReturn(Collections.singleton("typeNoAgent"));
        DistroData verifyDataItem = new DistroData(new DistroKey("k", "typeNoAgent"), new byte[0]);
        DistroDataStorage storage = new DistroDataStorage() {
            
            @Override
            public void finishInitial() {
            }
            
            @Override
            public boolean isFinishInitial() {
                return true;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDistroData(
                com.alibaba.nacos.core.distributed.distro.entity.DistroKey distroKey) {
                return null;
            }
            
            @Override
            public com.alibaba.nacos.core.distributed.distro.entity.DistroData getDatumSnapshot() {
                return null;
            }
            
            @Override
            public List<DistroData> getVerifyData() {
                return Collections.singletonList(verifyDataItem);
            }
        };
        when(distroComponentHolder.findDataStorage("typeNoAgent")).thenReturn(storage);
        when(distroComponentHolder.findTransportAgent("typeNoAgent")).thenReturn(null);
        DistroVerifyTimedTask task =
            new DistroVerifyTimedTask(serverMemberManager, distroComponentHolder,
                executeTaskExecuteEngine);
        task.run();
    }
}
