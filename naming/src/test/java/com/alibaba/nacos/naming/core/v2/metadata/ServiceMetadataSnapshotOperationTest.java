/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceMetadataSnapshotOperationTest {
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    private ServiceMetadataSnapshotOperation serviceMetadataSnapshotOperation;
    
    @BeforeEach
    void setUp() throws Exception {
        Map<Service, ServiceMetadata> map = new ConcurrentHashMap<>();
        map.put(Service.newService("namespace", "group", "name"), new ServiceMetadata());
        Mockito.when(namingMetadataManager.getServiceMetadataSnapshot()).thenReturn(map);
        serviceMetadataSnapshotOperation = new ServiceMetadataSnapshotOperation(
            namingMetadataManager, new ReentrantReadWriteLock());
    }
    
    @Test
    void testDumpSnapshot() {
        InputStream inputStream = serviceMetadataSnapshotOperation.dumpSnapshot();
        
        assertNotNull(inputStream);
    }
    
    @Test
    void testLoadSnapshot() {
        ConcurrentMap<Service, ServiceMetadata> map = new ConcurrentHashMap<>();
        Service service = Service.newService("namespace", "group", "name");
        map.put(service, new ServiceMetadata());
        
        Serializer aDefault = SerializeFactory.getDefault();
        serviceMetadataSnapshotOperation.loadSnapshot(aDefault.serialize(map));
        
        Map<Service, ServiceMetadata> serviceMetadataSnapshot =
            namingMetadataManager.getServiceMetadataSnapshot();
        assertNotNull(serviceMetadataSnapshot);
        assertEquals(1, serviceMetadataSnapshot.size());
    }
    
    @Test
    void testGetSnapshotArchive() {
        String snapshotArchive = serviceMetadataSnapshotOperation.getSnapshotArchive();
        
        assertEquals("service_metadata.zip", snapshotArchive);
    }
    
    @Test
    void testGetSnapshotSaveTag() {
        String snapshotSaveTag = serviceMetadataSnapshotOperation.getSnapshotSaveTag();
        
        assertEquals(snapshotSaveTag,
            ServiceMetadataSnapshotOperation.class.getSimpleName() + ".SAVE");
    }
    
    @Test
    void testGetSnapshotLoadTag() {
        String snapshotLoadTag = serviceMetadataSnapshotOperation.getSnapshotLoadTag();
        
        assertEquals(snapshotLoadTag,
            ServiceMetadataSnapshotOperation.class.getSimpleName() + ".LOAD");
    }
    
    @Test
    void testWriteSnapshotAddsArchiveWithChecksum(@TempDir Path tempDir) throws Exception {
        Writer writer = mockSnapshotWriter(tempDir);
        ArgumentCaptor<LocalFileMeta> metaCaptor = ArgumentCaptor.forClass(LocalFileMeta.class);
        Mockito.when(writer.addFile(Mockito.eq("service_metadata.zip"), metaCaptor.capture()))
            .thenReturn(true);
        
        boolean result = serviceMetadataSnapshotOperation.writeSnapshot(writer);
        
        assertTrue(result);
        assertTrue(Files.exists(tempDir.resolve("service_metadata.zip")));
        assertNotNull(metaCaptor.getValue().get("checksum"));
    }
    
    @Test
    void testReadSnapshotLoadsArchiveWhenChecksumMatches(@TempDir Path tempDir) throws Exception {
        LocalFileMeta meta = writeSnapshot(tempDir);
        Reader reader = mockSnapshotReader(tempDir, meta);
        
        boolean result = serviceMetadataSnapshotOperation.readSnapshot(reader);
        
        assertTrue(result);
        Mockito.verify(namingMetadataManager)
            .loadServiceMetadataSnapshot(Mockito.any(ConcurrentMap.class));
    }
    
    @Test
    void testReadSnapshotThrowsWhenChecksumMismatch(@TempDir Path tempDir) throws Exception {
        writeSnapshot(tempDir);
        LocalFileMeta wrongMeta = new LocalFileMeta();
        wrongMeta.append("checksum", "wrong");
        Reader reader = mockSnapshotReader(tempDir, wrongMeta);
        
        assertThrows(IllegalArgumentException.class,
            () -> serviceMetadataSnapshotOperation.readSnapshot(reader));
    }
    
    private LocalFileMeta writeSnapshot(Path tempDir) throws Exception {
        Writer writer = mockSnapshotWriter(tempDir);
        ArgumentCaptor<LocalFileMeta> metaCaptor = ArgumentCaptor.forClass(LocalFileMeta.class);
        Mockito.when(writer.addFile(Mockito.eq("service_metadata.zip"), metaCaptor.capture()))
            .thenReturn(true);
        
        serviceMetadataSnapshotOperation.writeSnapshot(writer);
        return metaCaptor.getValue();
    }
    
    private Writer mockSnapshotWriter(Path tempDir) {
        Writer result = Mockito.mock(Writer.class);
        Mockito.when(result.getPath()).thenReturn(tempDir.toString());
        return result;
    }
    
    private Reader mockSnapshotReader(Path tempDir, LocalFileMeta meta) {
        Reader result = Mockito.mock(Reader.class);
        Mockito.when(result.getPath()).thenReturn(tempDir.toString());
        Mockito.when(result.getFileMeta("service_metadata.zip")).thenReturn(meta);
        return result;
    }
}
