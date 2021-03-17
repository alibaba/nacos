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
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service metadata snapshot operation.
 *
 * @author xiweng.yy
 */
public class ServiceMetadataSnapshotOperation extends AbstractMetadataSnapshotOperation {
    
    private static final String SNAPSHOT_SAVE = ServiceMetadataSnapshotOperation.class.getSimpleName() + ".SAVE";
    
    private static final String SNAPSHOT_LOAD = ServiceMetadataSnapshotOperation.class.getSimpleName() + ".LOAD";
    
    private static final String SNAPSHOT_ARCHIVE = "service_metadata.zip";
    
    private final NamingMetadataManager metadataManager;
    
    private final Serializer serializer;
    
    public ServiceMetadataSnapshotOperation(NamingMetadataManager metadataManager, ReentrantReadWriteLock lock) {
        super(lock);
        this.metadataManager = metadataManager;
        this.serializer = SerializeFactory.getDefault();
    }
    
    @Override
    protected InputStream dumpSnapshot() {
        Map<Service, ServiceMetadata> snapshot = metadataManager.getServiceMetadataSnapshot();
        return new ByteArrayInputStream(serializer.serialize(snapshot));
    }
    
    @Override
    protected void loadSnapshot(byte[] snapshotBytes) {
        metadataManager.loadServiceMetadataSnapshot(serializer.deserialize(snapshotBytes));
    }
    
    @Override
    protected String getSnapshotArchive() {
        return SNAPSHOT_ARCHIVE;
    }
    
    @Override
    protected String getSnapshotSaveTag() {
        return SNAPSHOT_SAVE;
    }
    
    @Override
    protected String getSnapshotLoadTag() {
        return SNAPSHOT_LOAD;
    }
}
