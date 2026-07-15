/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BatchInstanceDataTest {
    
    @Test
    void testConstructWithArguments() {
        List<String> namespaces = Collections.singletonList("namespace");
        List<String> groupNames = Collections.singletonList("group");
        List<String> serviceNames = Collections.singletonList("service");
        List<BatchInstancePublishInfo> batchInfos =
            Collections.singletonList(new BatchInstancePublishInfo());
        
        BatchInstanceData data =
            new BatchInstanceData(namespaces, groupNames, serviceNames, batchInfos);
        
        assertSame(namespaces, data.getNamespaces());
        assertSame(groupNames, data.getGroupNames());
        assertSame(serviceNames, data.getServiceNames());
        assertSame(batchInfos, data.getBatchInstancePublishInfos());
    }
    
    @Test
    void testSettersAndGetters() {
        BatchInstanceData data = new BatchInstanceData();
        List<String> namespaces = Collections.singletonList("namespace");
        List<String> groupNames = Collections.singletonList("group");
        List<String> serviceNames = Collections.singletonList("service");
        List<BatchInstancePublishInfo> batchInfos =
            Collections.singletonList(new BatchInstancePublishInfo());
        
        data.setNamespaces(namespaces);
        data.setGroupNames(groupNames);
        data.setServiceNames(serviceNames);
        data.setBatchInstancePublishInfos(batchInfos);
        
        assertEquals(namespaces, data.getNamespaces());
        assertEquals(groupNames, data.getGroupNames());
        assertEquals(serviceNames, data.getServiceNames());
        assertEquals(batchInfos, data.getBatchInstancePublishInfos());
    }
}
