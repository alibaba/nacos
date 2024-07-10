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

package com.alibaba.nacos.naming.core.v2.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class MetadataOperationTest {
    
    private MetadataOperation<String> metadataOperation;
    
    @BeforeEach
    void setUp() {
        metadataOperation = new MetadataOperation();
    }
    
    @Test
    void testGetNamespace() {
        String namespace = metadataOperation.getNamespace();
        
        assertNull(namespace);
    }
    
    @Test
    void testSetNamespace() {
        String namespace = "2398479283749823984";
        metadataOperation.setNamespace(namespace);
        
        assertEquals(metadataOperation.getNamespace(), namespace);
    }
    
    @Test
    void testGetGroup() {
        String group = metadataOperation.getGroup();
        
        assertNull(group);
    }
    
    @Test
    void testSetGroup() {
        String group = "default";
        metadataOperation.setGroup(group);
        
        assertEquals(metadataOperation.getGroup(), group);
    }
    
    @Test
    void testGetServiceName() {
        String serviceName = metadataOperation.getServiceName();
        
        assertNull(serviceName);
    }
    
    @Test
    void testSetServiceName() {
        String serviceName = "nacos";
        metadataOperation.setServiceName(serviceName);
        
        assertEquals(metadataOperation.getServiceName(), serviceName);
    }
    
    @Test
    void testGetTag() {
        String tag = metadataOperation.getTag();
        
        assertNull(tag);
    }
    
    @Test
    void testSetTag() {
        String tag = "tag";
        metadataOperation.setTag(tag);
        
        assertEquals(metadataOperation.getTag(), tag);
    }
    
    @Test
    void testGetMetadata() {
        Object metadata = metadataOperation.getMetadata();
        
        assertNull(metadata);
    }
    
    @Test
    void testSetMetadata() {
        String metadata = "metadata";
        metadataOperation.setMetadata(metadata);
        
        assertEquals(metadataOperation.getMetadata(), metadata);
    }
}