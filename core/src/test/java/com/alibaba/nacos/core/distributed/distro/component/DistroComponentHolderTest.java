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

package com.alibaba.nacos.core.distributed.distro.component;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DistroComponentHolderTest extends TestCase {
    
    private final String type = "com.alibaba.nacos.naming.iplist.";
    
    private DistroComponentHolder componentHolder;
    
    @Mock
    private DistroDataStorage distroDataStorage;
    
    @Mock
    private DistroTransportAgent distroTransportAgent;
    
    @Mock
    private DistroFailedTaskHandler distroFailedTaskHandler;
    
    @Mock
    private DistroDataProcessor distroDataProcessor;
    
    @Before
    public void setUp() {
        componentHolder = new DistroComponentHolder();
        componentHolder.registerDataStorage(type, distroDataStorage);
        componentHolder.registerTransportAgent(type, distroTransportAgent);
        componentHolder.registerFailedTaskHandler(type, distroFailedTaskHandler);
        when(distroDataProcessor.processType()).thenReturn(type);
        componentHolder.registerDataProcessor(distroDataProcessor);
    }
    
    @Test
    public void testFindTransportAgent() {
        DistroTransportAgent distroTransportAgent = componentHolder.findTransportAgent(type);
        assertEquals(this.distroTransportAgent, distroTransportAgent);
    }
    
    @Test
    public void testFindDataStorage() {
        DistroDataStorage distroDataStorage = componentHolder.findDataStorage(type);
        assertEquals(this.distroDataStorage, distroDataStorage);
    }
    
    @Test
    public void testGetDataStorageTypes() {
        componentHolder.getDataStorageTypes();
    }
    
    @Test
    public void testFindFailedTaskHandler() {
        DistroFailedTaskHandler distroFailedTaskHandler = componentHolder.findFailedTaskHandler(type);
        assertEquals(this.distroFailedTaskHandler, distroFailedTaskHandler);
    }
    
    @Test
    public void testFindDataProcessor() {
        DistroDataProcessor distroDataProcessor = componentHolder.findDataProcessor(type);
        assertEquals(this.distroDataProcessor, distroDataProcessor);
    }
}