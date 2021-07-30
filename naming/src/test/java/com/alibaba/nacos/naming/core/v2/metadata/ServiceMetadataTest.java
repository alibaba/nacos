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

import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ServiceMetadataTest {
    
    @Mock
    private ClusterMetadata clusterMetadata;
    
    private ServiceMetadata serviceMetadata;
    
    @Before
    public void setUp() {
        serviceMetadata = new ServiceMetadata();
    }
    
    @Test
    public void testIsEphemeral() {
        Assert.assertTrue(serviceMetadata.isEphemeral());
    }
    
    @Test
    public void testSetEphemeral() {
        serviceMetadata.setEphemeral(false);
        Assert.assertFalse(serviceMetadata.isEphemeral());
    }
    
    @Test
    public void testGetProtectThreshold() {
        Assert.assertEquals(serviceMetadata.getProtectThreshold(), 0.0F, 0);
    }
    
    @Test
    public void testSetProtectThreshold() {
        serviceMetadata.setProtectThreshold(1.0F);
        Assert.assertEquals(serviceMetadata.getProtectThreshold(), 1.0F, 0);
    }
    
    @Test
    public void testGetSelector() {
        Selector selector = serviceMetadata.getSelector();
        
        Assert.assertNotNull(selector);
        boolean result = selector instanceof NoneSelector;
        Assert.assertTrue(result);
    }
    
    @Test
    public void testSetSelector() {
        LabelSelector labelSelector = new LabelSelector();
        serviceMetadata.setSelector(labelSelector);
        
        Selector selector = serviceMetadata.getSelector();
        Assert.assertNotNull(selector);
        boolean result = selector instanceof LabelSelector;
        Assert.assertTrue(result);
    }
    
    @Test
    public void testGetExtendData() {
        Map<String, String> extendData = serviceMetadata.getExtendData();
        
        Assert.assertNotNull(extendData);
        Assert.assertEquals(extendData.size(), 0);
    }
    
    @Test
    public void testSetExtendData() {
        Map<String, String> extendData = new HashMap<>();
        extendData.put("nacos", "nacos");
        serviceMetadata.setExtendData(extendData);
        
        Map<String, String> map = serviceMetadata.getExtendData();
        Assert.assertNotNull(map);
        Assert.assertEquals(map.size(), 1);
    }
    
    @Test
    public void testGetClusters() {
        Map<String, ClusterMetadata> clusters = serviceMetadata.getClusters();
        
        Assert.assertNotNull(clusters);
        Assert.assertEquals(clusters.size(), 0);
    }
    
    @Test
    public void testSetClusters() {
        Map<String, ClusterMetadata> clusters = new HashMap<>();
        clusters.put("key", clusterMetadata);
        serviceMetadata.setClusters(clusters);
        
        Map<String, ClusterMetadata> map = serviceMetadata.getClusters();
        Assert.assertNotNull(map);
        Assert.assertEquals(map.size(), 1);
    }
    
    @Test
    public void testEquals() {
        ServiceMetadata serviceMetadata1 = new ServiceMetadata();
        ServiceMetadata serviceMetadata2 = new ServiceMetadata();
        
        boolean equals = serviceMetadata1.equals(serviceMetadata2);
        Assert.assertFalse(equals);
    }
}