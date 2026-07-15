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

package com.alibaba.nacos.core.distributed.distro.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DistroRecord} unit test.
 */
class DistroRecordTest {
    
    private static final String TYPE = "testType";
    
    private DistroRecord distroRecord;
    
    @BeforeEach
    void setUp() {
        distroRecord = new DistroRecord(TYPE);
    }
    
    @Test
    void testGetType() {
        assertEquals(TYPE, distroRecord.getType());
    }
    
    @Test
    void testSyncSuccess() {
        distroRecord.syncSuccess();
        distroRecord.syncSuccess();
        assertEquals(2, distroRecord.getTotalSyncCount());
        assertEquals(2, distroRecord.getSuccessfulSyncCount());
        assertEquals(0, distroRecord.getFailedSyncCount());
    }
    
    @Test
    void testSyncFail() {
        distroRecord.syncFail();
        distroRecord.syncFail();
        assertEquals(2, distroRecord.getTotalSyncCount());
        assertEquals(0, distroRecord.getSuccessfulSyncCount());
        assertEquals(2, distroRecord.getFailedSyncCount());
    }
    
    @Test
    void testVerifyFail() {
        distroRecord.verifyFail();
        distroRecord.verifyFail();
        distroRecord.verifyFail();
        assertEquals(3, distroRecord.getFailedVerifyCount());
    }
    
    @Test
    void testMixedSyncAndVerify() {
        distroRecord.syncSuccess();
        distroRecord.syncFail();
        distroRecord.verifyFail();
        assertEquals(2, distroRecord.getTotalSyncCount());
        assertEquals(1, distroRecord.getSuccessfulSyncCount());
        assertEquals(1, distroRecord.getFailedSyncCount());
        assertEquals(1, distroRecord.getFailedVerifyCount());
    }
}
