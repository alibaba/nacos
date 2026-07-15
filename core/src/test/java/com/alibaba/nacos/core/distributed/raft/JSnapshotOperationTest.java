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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for {@link JSnapshotOperation} default method.
 */
class JSnapshotOperationTest {
    
    @Test
    void testBuildMetadataWithNull() throws Exception {
        JSnapshotOperation op = new JSnapshotOperation() {
            
            @Override
            public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            }
            
            @Override
            public boolean onSnapshotLoad(SnapshotReader reader) {
                return false;
            }
            
            @Override
            public String info() {
                return "test";
            }
        };
        assertNull(op.buildMetadata(null));
    }
    
    @Test
    void testBuildMetadataWithLocalFileMeta() throws Exception {
        LocalFileMeta meta = new LocalFileMeta();
        meta.append("key", "value");
        JSnapshotOperation op = new JSnapshotOperation() {
            
            @Override
            public void onSnapshotSave(SnapshotWriter writer, Closure done) {
            }
            
            @Override
            public boolean onSnapshotLoad(SnapshotReader reader) {
                return false;
            }
            
            @Override
            public String info() {
                return "test";
            }
        };
        assertNotNull(op.buildMetadata(meta));
    }
}
