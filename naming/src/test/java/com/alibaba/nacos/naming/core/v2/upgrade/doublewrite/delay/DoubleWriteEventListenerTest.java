/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay;

import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link DoubleWriteEventListener} unit tests.
 *
 * @author chenglu
 * @date 2021-09-01 23:34
 */
@RunWith(MockitoJUnitRunner.class)
public class DoubleWriteEventListenerTest {
    
    private DoubleWriteEventListener doubleWriteEventListener;
    
    @Mock
    private UpgradeJudgement upgradeJudgement;
    
    @Mock
    private DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;
    
    @Before
    public void setUp() {
        EnvUtil.setIsStandalone(false);
        doubleWriteEventListener = new DoubleWriteEventListener(upgradeJudgement, doubleWriteDelayTaskEngine);
    }
    
    @After
    public void tearDown() {
        EnvUtil.setIsStandalone(true);
    }
    
    @Test
    public void testOnEvent() {
        Mockito.when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(true);
        
        Service service = Service.newService("A", "B", "C");
        ServiceEvent.ServiceChangedEvent serviceChangedEvent = new ServiceEvent.ServiceChangedEvent(service);
        doubleWriteEventListener.onEvent(serviceChangedEvent);
    
        Mockito.verify(doubleWriteDelayTaskEngine).addTask(Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testDoubleWriteMetadataToV1() {
        Mockito.when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(true);
        
        Service service = Service.newService("A", "B", "C");
        doubleWriteEventListener.doubleWriteMetadataToV1(service, false);
    
        Mockito.verify(doubleWriteDelayTaskEngine).addTask(Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testDoubleWriteToV2() {
        Mockito.when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(false);
        Mockito.when(upgradeJudgement.isAll20XVersion()).thenReturn(false);
        
        com.alibaba.nacos.naming.core.Service service = new com.alibaba.nacos.naming.core.Service();
        doubleWriteEventListener.doubleWriteToV2(service, false);
    
        Mockito.verify(doubleWriteDelayTaskEngine).addTask(Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testDoubleWriteMetadataToV2() {
        Mockito.when(upgradeJudgement.isUseGrpcFeatures()).thenReturn(false);
        Mockito.when(upgradeJudgement.isAll20XVersion()).thenReturn(false);
        
        com.alibaba.nacos.naming.core.Service service = new com.alibaba.nacos.naming.core.Service();
        doubleWriteEventListener.doubleWriteMetadataToV2(service, false, false);
    
        Mockito.verify(doubleWriteDelayTaskEngine).addTask(Mockito.any(), Mockito.any());
    }
}
