/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.lock.core.NLock;
import com.alibaba.nacos.client.lock.core.NLockFactory;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NacosLockServiceTest {
    
    @Mock
    private LockGrpcClient lockGrpcClient;
    
    private NacosLockService lockService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        lockService = new NacosLockService(properties);
        injectMock();
    }
    
    private void injectMock() throws NoSuchFieldException, IllegalAccessException {
        Field lockGrpcClientField = NacosLockService.class.getDeclaredField("lockGrpcClient");
        lockGrpcClientField.setAccessible(true);
        lockGrpcClientField.set(lockService, lockGrpcClient);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        lockService.shutdown();
    }
    
    @Test
    void lock() throws NacosException {
        NLock nLock = NLockFactory.getLock("test");
        lockService.lock(nLock);
        verify(lockGrpcClient).lock(nLock);
    }
    
    @Test
    void unLock() throws NacosException {
        NLock nLock = NLockFactory.getLock("test");
        lockService.unLock(nLock);
        verify(lockGrpcClient).unLock(nLock);
    }
}