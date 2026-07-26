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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class InstanceMetadataSnapshotOperationTest {
    
    private static final Service SERVICE = Service.newService("namespace", "group", "name");
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    private InstanceMetadataSnapshotOperation snapshotOperation;
    
    @BeforeEach
    void setUp() {
        Map<Service, ConcurrentMap<String, InstanceMetadata>> snapshot =
            new ConcurrentHashMap<>();
        ConcurrentMap<String, InstanceMetadata> instanceMetadata = new ConcurrentHashMap<>();
        instanceMetadata.put("1.1.1.1:8848", new InstanceMetadata());
        snapshot.put(SERVICE, instanceMetadata);
        Mockito.lenient().when(namingMetadataManager.getInstanceMetadataSnapshot())
            .thenReturn(snapshot);
        snapshotOperation =
            new InstanceMetadataSnapshotOperation(namingMetadataManager,
                new ReentrantReadWriteLock());
    }
    
    @Test
    void testDumpSnapshot() {
        InputStream inputStream = snapshotOperation.dumpSnapshot();
        
        assertNotNull(inputStream);
    }
    
    @Test
    void testLoadSnapshot() {
        ConcurrentMap<Service, ConcurrentMap<String, InstanceMetadata>> snapshot =
            new ConcurrentHashMap<>();
        ConcurrentMap<String, InstanceMetadata> instanceMetadata = new ConcurrentHashMap<>();
        instanceMetadata.put("1.1.1.1:8848", new InstanceMetadata());
        snapshot.put(SERVICE, instanceMetadata);
        Serializer serializer = SerializeFactory.getDefault();
        
        snapshotOperation.loadSnapshot(serializer.serialize(snapshot));
        
        Mockito.verify(namingMetadataManager)
            .loadInstanceMetadataSnapshot(Mockito.any(ConcurrentMap.class));
    }
    
    @Test
    void testGetSnapshotArchive() {
        assertEquals("instance_metadata.zip", snapshotOperation.getSnapshotArchive());
    }
    
    @Test
    void testGetSnapshotSaveTag() {
        assertEquals(InstanceMetadataSnapshotOperation.class.getSimpleName() + ".SAVE",
            snapshotOperation.getSnapshotSaveTag());
    }
    
    @Test
    void testGetSnapshotLoadTag() {
        assertEquals(InstanceMetadataSnapshotOperation.class.getSimpleName() + ".LOAD",
            snapshotOperation.getSnapshotLoadTag());
    }
}
