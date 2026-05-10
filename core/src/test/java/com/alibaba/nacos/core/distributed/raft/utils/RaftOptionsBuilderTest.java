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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.option.ReadOnlyOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RaftOptionsBuilderTest {
    
    @Test
    void testInitRaftOptionsWithDefaultReadOnlySafe() {
        RaftConfig config = new RaftConfig();
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertEquals(ReadOnlyOption.ReadOnlySafe, options.getReadOnlyOptions());
    }
    
    @Test
    void testInitRaftOptionsWithReadOnlyLeaseBased() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_READ_INDEX_TYPE, "ReadOnlyLeaseBased");
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertEquals(ReadOnlyOption.ReadOnlyLeaseBased, options.getReadOnlyOptions());
    }
    
    @Test
    void testInitRaftOptionsWithBlankReadIndexTypeDefaultsToReadOnlySafe() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_READ_INDEX_TYPE, "");
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertEquals(ReadOnlyOption.ReadOnlySafe, options.getReadOnlyOptions());
    }
    
    @Test
    void testInitRaftOptionsWithExplicitReadOnlySafe() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_READ_INDEX_TYPE, "ReadOnlySafe");
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertEquals(ReadOnlyOption.ReadOnlySafe, options.getReadOnlyOptions());
    }
    
    @Test
    void testInitRaftOptionsWithInvalidReadIndexTypeThrows() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_READ_INDEX_TYPE, "Invalid");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> RaftOptionsBuilder.initRaftOptions(config));
        assertEquals(
            "Illegal Raft system parameters => ReadOnlyOption : [Invalid], should be 'ReadOnlySafe' or 'ReadOnlyLeaseBased'",
            ex.getMessage());
    }
    
    @Test
    void testInitRaftOptionsWithCustomNumericConfigReturnsNonNull() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.MAX_ENTRIES_SIZE, "2048");
        config.setVal(RaftSysConstants.APPLY_BATCH, "64");
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertNotNull(options);
        assertEquals(ReadOnlyOption.ReadOnlySafe, options.getReadOnlyOptions());
    }
    
    @Test
    void testInitRaftOptionsWithBooleanConfig() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.SYNC, "false");
        config.setVal(RaftSysConstants.SYNC_META, "true");
        config.setVal(RaftSysConstants.REPLICATOR_PIPELINE, "false");
        config.setVal(RaftSysConstants.ENABLE_LOG_ENTRY_CHECKSUM, "true");
        RaftOptions options = RaftOptionsBuilder.initRaftOptions(config);
        assertNotNull(options);
        assertEquals(ReadOnlyOption.ReadOnlySafe, options.getReadOnlyOptions());
    }
}
