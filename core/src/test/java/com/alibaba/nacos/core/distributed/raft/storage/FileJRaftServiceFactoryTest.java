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

package com.alibaba.nacos.core.distributed.raft.storage;

import com.alibaba.nacos.core.distributed.raft.storage.file.FileLogStorage;
import com.alipay.sofa.jraft.JRaftServiceFactory;
import com.alipay.sofa.jraft.core.DefaultJRaftServiceFactory;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;
import com.alipay.sofa.jraft.storage.SnapshotStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class FileJRaftServiceFactoryTest {
    
    @TempDir
    Path directory;
    
    @Test
    void testFileLogsPreserveDefaultSnapshotMetadataAndCodec() {
        JRaftServiceFactory factory = new FileJRaftServiceFactory();
        DefaultJRaftServiceFactory defaults = new DefaultJRaftServiceFactory();
        RaftOptions raftOptions = new RaftOptions();
        assertInstanceOf(FileLogStorage.class,
            factory.createLogStorage(directory.resolve("log").toString(), raftOptions));
        NodeOptions options = new NodeOptions();
        options.setRaftOptions(raftOptions);
        options.setSnapshotUri(directory.resolve("snapshot").toString());
        SnapshotStorage snapshot = factory.createSnapshotStorage(options);
        assertEquals(defaults.createSnapshotStorage(options).getClass(), snapshot.getClass());
        String metaUri = directory.resolve("meta").toString();
        RaftMetaStorage metadata = factory.createRaftMetaStorage(metaUri, raftOptions);
        assertEquals(defaults.createRaftMetaStorage(metaUri, raftOptions).getClass(),
            metadata.getClass());
        assertSame(defaults.createLogEntryCodecFactory(), factory.createLogEntryCodecFactory());
    }
}
