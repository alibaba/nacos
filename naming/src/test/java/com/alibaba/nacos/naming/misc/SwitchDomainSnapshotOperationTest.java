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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SwitchDomainSnapshotOperationTest {
    
    private static final String SNAPSHOT_DIR = "naming_persistent";
    
    private static final String SNAPSHOT_ARCHIVE = "naming_persistent.zip";
    
    @Mock
    private SwitchManager switchManager;
    
    @Mock
    private Serializer serializer;
    
    @Test
    void testWriteAndReadSnapshot(@TempDir Path snapshotPath) {
        SwitchDomainSnapshotOperation operation = newSnapshotOperation();
        Writer writer = new Writer(snapshotPath.toString());
        
        Boolean writeResult = ReflectionTestUtils.invokeMethod(operation, "writeSnapshot", writer);
        
        assertTrue(writeResult);
        assertTrue(writer.listFiles().containsKey(SNAPSHOT_ARCHIVE));
        verify(switchManager).dumpSnapshot(snapshotPath.resolve(SNAPSHOT_DIR).toString());
        assertFalse(Files.exists(snapshotPath.resolve(SNAPSHOT_DIR)));
        
        Reader reader = new Reader(snapshotPath.toString(), writer.listFiles());
        assertTrue(operation.onSnapshotLoad(reader));
        
        verify(switchManager).loadSnapshot(snapshotPath.resolve(SNAPSHOT_DIR).toString());
        assertFalse(Files.exists(snapshotPath.resolve(SNAPSHOT_DIR)));
    }
    
    @Test
    void testReadSnapshotReturnsFalseForChecksumMismatch(@TempDir Path snapshotPath) {
        SwitchDomainSnapshotOperation operation = newSnapshotOperation();
        Writer writer = new Writer(snapshotPath.toString());
        ReflectionTestUtils.invokeMethod(operation, "writeSnapshot", writer);
        LocalFileMeta badMeta = new LocalFileMeta().append("checksum", "bad");
        Reader reader = new Reader(snapshotPath.toString(),
            Collections.singletonMap(SNAPSHOT_ARCHIVE, badMeta));
        
        assertFalse(operation.onSnapshotLoad(reader));
        
        verify(switchManager, times(0)).loadSnapshot(eq(Paths.get(snapshotPath.toString(),
            SNAPSHOT_DIR).toString()));
    }
    
    @Test
    void testSnapshotTags() {
        SwitchDomainSnapshotOperation operation = newSnapshotOperation();
        
        String saveTag = ReflectionTestUtils.invokeMethod(operation, "getSnapshotSaveTag");
        String loadTag = ReflectionTestUtils.invokeMethod(operation, "getSnapshotLoadTag");
        
        assertTrue(saveTag.endsWith(".SAVE"));
        assertTrue(loadTag.endsWith(".LOAD"));
    }
    
    private SwitchDomainSnapshotOperation newSnapshotOperation() {
        return new SwitchDomainSnapshotOperation(new ReentrantReadWriteLock(), switchManager,
            serializer);
    }
}
