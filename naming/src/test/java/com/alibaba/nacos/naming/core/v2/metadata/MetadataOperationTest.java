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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MetadataOperationTest {
    
    private MetadataOperation<String> metadataOperation;
    
    @Before
    public void setUp() {
        metadataOperation = new MetadataOperation();
    }
    
    @Test
    public void testGetNamespace() {
        String namespace = metadataOperation.getNamespace();
        
        Assert.assertNull(namespace);
    }
    
    @Test
    public void testSetNamespace() {
        String namespace = "2398479283749823984";
        metadataOperation.setNamespace(namespace);
        
        Assert.assertEquals(metadataOperation.getNamespace(), namespace);
    }
    
    @Test
    public void testGetGroup() {
        String group = metadataOperation.getGroup();
        
        Assert.assertNull(group);
    }
    
    @Test
    public void testSetGroup() {
        String group = "default";
        metadataOperation.setGroup(group);
        
        Assert.assertEquals(metadataOperation.getGroup(), group);
    }
    
    @Test
    public void testGetServiceName() {
        String serviceName = metadataOperation.getServiceName();
        
        Assert.assertNull(serviceName);
    }
    
    @Test
    public void testSetServiceName() {
        String serviceName = "nacos";
        metadataOperation.setServiceName(serviceName);
        
        Assert.assertEquals(metadataOperation.getServiceName(), serviceName);
    }
    
    @Test
    public void testGetTag() {
        String tag = metadataOperation.getTag();
        
        Assert.assertNull(tag);
    }
    
    @Test
    public void testSetTag() {
        String tag = "tag";
        metadataOperation.setTag(tag);
        
        Assert.assertEquals(metadataOperation.getTag(), tag);
    }
    
    @Test
    public void testGetMetadata() {
        Object metadata = metadataOperation.getMetadata();
        
        Assert.assertNull(metadata);
    }
    
    @Test
    public void testSetMetadata() {
        String metadata = "metadata";
        metadataOperation.setMetadata(metadata);
        
        Assert.assertEquals(metadataOperation.getMetadata(), metadata);
    }
}