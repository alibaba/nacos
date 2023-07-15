/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.core.service.impl;

import com.alibaba.nacos.api.lock.model.LockInfo;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.constants.Constants;
import com.alibaba.nacos.lock.core.LockManager;
import com.alibaba.nacos.lock.core.NacosLockManager;
import com.alibaba.nacos.lock.core.connect.ConnectionManager;
import com.alibaba.nacos.lock.enums.ConnectTypeEnum;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;


/**
 * mutex operation service test.
 *
 * @author qiyue.zhang@aloudata.com
 * @description MutexOperationServiceImplTest
 * @date 2023/7/13 20:06
 */
@RunWith(MockitoJUnitRunner.class)
public class MutexOperationServiceImplTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol protocol;
    
    private LockManager lockManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private MutexOperationServiceImpl mutexOperationService;
    
    private MockedStatic<ApplicationUtils> mockUtil;
    
    @Before
    public void setUp() throws Exception {
        mockUtil = Mockito.mockStatic(ApplicationUtils.class);
        mockUtil.when(() -> ApplicationUtils.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(protocol);
        
        Response response = Response.newBuilder().setSuccess(true)
                .setData(ByteString.copyFrom(serializer.serialize(true))).build();
        Mockito.when(protocol.write(Mockito.any())).thenReturn(response);
        lockManager = new NacosLockManager();
        mutexOperationService = new MutexOperationServiceImpl(lockManager, connectionManager);
    }
    
    @After
    public void destroy() {
        mockUtil.close();
    }
    
    /**
     * create default lock info.
     *
     * @return LockInfo
     */
    public LockInfo createDefaultLockInfo() {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setKey("key");
        lockInfo.setLockInstance(new LockInstance("1.1.1.1", 8080));
        return lockInfo;
    }
    
    @Test
    public void protocolTest() {
        assertTrue(mutexOperationService.lock(createDefaultLockInfo(), "connectId"));
    }
    
    @Test
    public void acquireLockTest() {
        Mockito.when(connectionManager.isAlive(Mockito.any())).thenReturn(true);
        
        LockInfo lockInfo = createDefaultLockInfo();
        MutexOperationServiceImpl.MutexLockRequest request = new MutexOperationServiceImpl.MutexLockRequest();
        request.setLockInfo(lockInfo);
        request.setConnectionId("connectId");
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
                .setData(ByteString.copyFrom(serializer.serialize(request))).setOperation(LockOperation.ACQUIRE.name())
                .build();
        ByteString bytes = ByteString.copyFrom(serializer.serialize(true));
        assertEquals(mutexOperationService.onApply(writeRequest).getData(), bytes);
    }
    
    @Test
    public void releaseLockTest() {
        //acquire lock first, and then release.
        acquireLockTest();
        
        LockInfo lockInfo = createDefaultLockInfo();
        MutexOperationServiceImpl.MutexLockRequest request = new MutexOperationServiceImpl.MutexLockRequest();
        request.setLockInfo(lockInfo);
        request.setConnectionId("connectId");
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
                .setData(ByteString.copyFrom(serializer.serialize(request))).setOperation(LockOperation.RELEASE.name())
                .build();
        
        ByteString bytes = ByteString.copyFrom(serializer.serialize(true));
        assertEquals(mutexOperationService.onApply(writeRequest).getData(), bytes);
    }
    
    @Test
    public void connectedTest() {
        doAnswer((invocationOnMock -> {
            assertEquals(invocationOnMock.getArgument(0), "connectId");
            return null;
        })).when(connectionManager).createConnectionSync(Mockito.any());
        MutexOperationServiceImpl.ConnectedLockRequest request = new MutexOperationServiceImpl.ConnectedLockRequest();
        request.setConnectionId("connectId");
        request.setConnectType(ConnectTypeEnum.GRPC);
        
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(group())
                .setData(ByteString.copyFrom(serializer.serialize(request)))
                .setOperation(LockOperation.CONNECTED.name()).build();
        
        ByteString bytes = ByteString.copyFrom(serializer.serialize(true));
        assertEquals(mutexOperationService.onApply(writeRequest).getData(), bytes);
    }
    
    public String group() {
        return Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;
    }
}
