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

package com.alibaba.nacos.lock.service.impl;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.alibaba.nacos.lock.constant.Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lock operation service test.
 *
 * @author 985492783@qq.com
 * @date 2023/8/30 14:01
 */
@ExtendWith(MockitoExtension.class)
public class LockOperationServiceImplTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private static CPProtocol cpProtocol;
    
    @Mock
    private static LockManager lockManager;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private LockOperationServiceImpl lockOperationService;
    
    private static MockedStatic<ApplicationUtils> mockedStatic;
    
    private static MockedStatic<EnvUtil> mockedEnv;
    
    @BeforeAll
    public static void setUp() {
        mockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        mockedEnv = Mockito.mockStatic(EnvUtil.class);
        mockedEnv.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(ins -> ins.getArgument(2));
    }
    
    /**
     * build test service.
     */
    public void buildService() {
        mockedStatic.when(() -> ApplicationUtils.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        lockOperationService = Mockito.spy(new LockOperationServiceImpl(lockManager));
    }
    
    @Test
    public void testGroup() {
        buildService();
        
        assertEquals(lockOperationService.group(), LOCK_ACQUIRE_SERVICE_GROUP_V2);
    }
    
    @Test
    public void testLockExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(LockConstants.NACOS_LOCK_TYPE, lockInfo.getKey().getLockType());
            assertEquals(timestamp + PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME, (long) lockInfo.getEndTime());
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", -1L, LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testLockSimple() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(lockInfo.getKey().getLockType(), LockConstants.NACOS_LOCK_TYPE);
            assertEquals((long) lockInfo.getEndTime(), timestamp + 1_000L);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", 1_000L, LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testLockMaxExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInfo lockInfo = mutexLockRequest.getLockInfo();
            assertEquals(lockInfo.getKey().getLockType(), LockConstants.NACOS_LOCK_TYPE);
            assertEquals((long) lockInfo.getEndTime(), timestamp + PropertiesConstant.MAX_AUTO_EXPIRE_TIME);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", PropertiesConstant.MAX_AUTO_EXPIRE_TIME + 1_000L,
                LockConstants.NACOS_LOCK_TYPE);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testOnApply() {
        buildService();
        Mockito.when(lockManager.getMutexLock(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key")))
                .thenReturn(new MutexAtomicLock("key"));
        
        WriteRequest request = getRequest(LockOperationEnum.ACQUIRE);
        Response response = lockOperationService.onApply(request);
        assertTrue(response.getSuccess());
        assertTrue(serializer.<Boolean>deserialize(response.getData().toByteArray()));
    }
    
    public WriteRequest getRequest(LockOperationEnum lockOperationEnum) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        LockInfo lockInfo = new LockInfo();
        lockInfo.setEndTime(1L + System.currentTimeMillis());
        lockInfo.setKey(new LockKey(LockConstants.NACOS_LOCK_TYPE, "key"));
        mutexLockRequest.setLockInfo(lockInfo);
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(lockOperationService.group())
                .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
                .setOperation(lockOperationEnum.name()).build();
        return writeRequest;
    }
    
    public Response getResponse() {
        return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(true))).build();
    }
    
    @AfterAll
    public static void destroy() {
        mockedStatic.close();
        mockedEnv.close();
    }
}
