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

package com.alibaba.nacos.core.distributed.distro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistroConstantsTest {
    
    @Test
    void testModuleAndDataSyncConstants() {
        assertEquals("distro", DistroConstants.DISTRO_MODULE);
        assertEquals("nacos.core.protocol.distro.data.sync.delayMs",
            DistroConstants.DATA_SYNC_DELAY_MILLISECONDS);
        assertEquals("data_sync_delayMs", DistroConstants.DATA_SYNC_DELAY_MILLISECONDS_STATE);
        assertEquals(1000L, DistroConstants.DEFAULT_DATA_SYNC_DELAY_MILLISECONDS);
        assertEquals("nacos.core.protocol.distro.data.sync.timeoutMs",
            DistroConstants.DATA_SYNC_TIMEOUT_MILLISECONDS);
        assertEquals("data_sync_timeoutMs", DistroConstants.DATA_SYNC_TIMEOUT_MILLISECONDS_STATE);
        assertEquals(3000L, DistroConstants.DEFAULT_DATA_SYNC_TIMEOUT_MILLISECONDS);
        assertEquals("nacos.core.protocol.distro.data.sync.retryDelayMs",
            DistroConstants.DATA_SYNC_RETRY_DELAY_MILLISECONDS);
        assertEquals("data_sync_retryDelayMs",
            DistroConstants.DATA_SYNC_RETRY_DELAY_MILLISECONDS_STATE);
        assertEquals(3000L, DistroConstants.DEFAULT_DATA_SYNC_RETRY_DELAY_MILLISECONDS);
    }
    
    @Test
    void testDataVerifyConstants() {
        assertEquals("nacos.core.protocol.distro.data.verify.intervalMs",
            DistroConstants.DATA_VERIFY_INTERVAL_MILLISECONDS);
        assertEquals("data_verify_intervalMs",
            DistroConstants.DATA_VERIFY_INTERVAL_MILLISECONDS_STATE);
        assertEquals(5000L, DistroConstants.DEFAULT_DATA_VERIFY_INTERVAL_MILLISECONDS);
        assertEquals("nacos.core.protocol.distro.data.verify.timeoutMs",
            DistroConstants.DATA_VERIFY_TIMEOUT_MILLISECONDS);
        assertEquals("data_verify_timeoutMs",
            DistroConstants.DATA_VERIFY_TIMEOUT_MILLISECONDS_STATE);
        assertEquals(3000L, DistroConstants.DEFAULT_DATA_VERIFY_TIMEOUT_MILLISECONDS);
    }
    
    @Test
    void testDataLoadConstants() {
        assertEquals("nacos.core.protocol.distro.data.load.retryDelayMs",
            DistroConstants.DATA_LOAD_RETRY_DELAY_MILLISECONDS);
        assertEquals("data_load_retryDelayMs",
            DistroConstants.DATA_LOAD_RETRY_DELAY_MILLISECONDS_STATE);
        assertEquals(30000L, DistroConstants.DEFAULT_DATA_LOAD_RETRY_DELAY_MILLISECONDS);
        assertEquals("nacos.core.protocol.distro.data.load.timeoutMs",
            DistroConstants.DATA_LOAD_TIMEOUT_MILLISECONDS);
        assertEquals("data_load_timeoutMs", DistroConstants.DATA_LOAD_TIMEOUT_MILLISECONDS_STATE);
        assertEquals(30000L, DistroConstants.DEFAULT_DATA_LOAD_TIMEOUT_MILLISECONDS);
    }
}
