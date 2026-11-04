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
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.storage.RaftMetaStorage;
import com.alipay.sofa.jraft.storage.SnapshotStorage;

import java.nio.file.Path;

/**
 * Uses file-backed Raft logs while retaining JRaft's default metadata, snapshot and codec services.
 */
public final class FileJRaftServiceFactory implements JRaftServiceFactory {
    
    private final JRaftServiceFactory delegate = new DefaultJRaftServiceFactory();
    
    @Override
    public LogStorage createLogStorage(String uri, RaftOptions raftOptions) {
        return new FileLogStorage(Path.of(uri), raftOptions);
    }
    
    @Override
    public SnapshotStorage createSnapshotStorage(NodeOptions nodeOptions) {
        return delegate.createSnapshotStorage(nodeOptions);
    }
    
    @Override
    public RaftMetaStorage createRaftMetaStorage(String uri, RaftOptions raftOptions) {
        return delegate.createRaftMetaStorage(uri, raftOptions);
    }
    
    @Override
    public LogEntryCodecFactory createLogEntryCodecFactory() {
        return delegate.createLogEntryCodecFactory();
    }
}
