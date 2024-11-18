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

import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ServiceMetadataTest {
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    private ServiceMetadata serviceMetadata;
    
    @BeforeEach
    void setUp() {
        serviceMetadata = new ServiceMetadata();
    }
    
    @Test
    void testIsEphemeral() {
        assertTrue(serviceMetadata.isEphemeral());
    }
    
    @Test
    void testSetEphemeral() {
        serviceMetadata.setEphemeral(false);
        assertFalse(serviceMetadata.isEphemeral());
    }
    
    @Test
    void testGetProtectThreshold() {
        assertEquals(0.0F, serviceMetadata.getProtectThreshold(), 0);
    }
    
    @Test
    void testSetProtectThreshold() {
        serviceMetadata.setProtectThreshold(1.0F);
        assertEquals(1.0F, serviceMetadata.getProtectThreshold(), 0);
    }
    
    @Test
    void testGetSelector() {
        Selector selector = serviceMetadata.getSelector();
        
        assertNotNull(selector);
        boolean result = selector instanceof NoneSelector;
        assertTrue(result);
    }
    
    @Test
    void testSetSelector() {
        LabelSelector labelSelector = new LabelSelector();
        serviceMetadata.setSelector(labelSelector);
        
        Selector selector = serviceMetadata.getSelector();
        assertNotNull(selector);
        boolean result = selector instanceof LabelSelector;
        assertTrue(result);
    }
    
    @Test
    void testGetExtendData() {
        Map<String, String> extendData = serviceMetadata.getExtendData();
        
        assertNotNull(extendData);
        assertEquals(0, extendData.size());
    }
    
    @Test
    void testSetExtendData() {
        Map<String, String> extendData = new HashMap<>();
        extendData.put("nacos", "nacos");
        serviceMetadata.setExtendData(extendData);
        
        Map<String, String> map = serviceMetadata.getExtendData();
        assertNotNull(map);
        assertEquals(1, map.size());
    }
    
    @Test
    void testGetClusters() {
        Map<String, ClusterMetadata> clusters = serviceMetadata.getClusters();
        
        assertNotNull(clusters);
        assertEquals(0, clusters.size());
    }
    
    @Test
    void testSetClusters() {
        Map<String, ClusterMetadata> clusters = new HashMap<>();
        clusters.put("key", clusterMetadata);
        serviceMetadata.setClusters(clusters);
        
        Map<String, ClusterMetadata> map = serviceMetadata.getClusters();
        assertNotNull(map);
        assertEquals(1, map.size());
    }
    
    @Test
    void testEquals() {
        ServiceMetadata serviceMetadata1 = new ServiceMetadata();
        ServiceMetadata serviceMetadata2 = new ServiceMetadata();
        
        boolean equals = serviceMetadata1.equals(serviceMetadata2);
        assertFalse(equals);
    }
}