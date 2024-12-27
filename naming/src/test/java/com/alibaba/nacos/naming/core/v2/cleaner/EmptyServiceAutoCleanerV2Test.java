/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link EmptyServiceAutoCleanerV2} unit test.
 *
 * @author chenglu
 * @date 2021-07-21 12:40
 */
@ExtendWith(MockitoExtension.class)
class EmptyServiceAutoCleanerV2Test {
    
    @Mock
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    private EmptyServiceAutoCleanerV2 emptyServiceAutoCleanerV2;
    
    @Mock
    private Service service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        emptyServiceAutoCleanerV2 = new EmptyServiceAutoCleanerV2(clientServiceIndexesManager, serviceStorage);
        Mockito.when(service.getNamespace()).thenReturn("public");
        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.getSingleton(service);
    }
    
    @AfterEach
    void tearDown() {
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    void testGetType() {
        assertEquals("emptyService", emptyServiceAutoCleanerV2.getType());
    }
    
    @Test
    void testDoClean() {
        try {
            Mockito.when(clientServiceIndexesManager.getAllClientsRegisteredService(Mockito.any())).thenReturn(Collections.emptyList());
            
            Mockito.when(service.getLastUpdatedTime()).thenReturn(0L);
            
            emptyServiceAutoCleanerV2.doClean();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
