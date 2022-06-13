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

package com.alibaba.nacos.core.distributed.distro.monitor;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DistroRecordsHolderTest {
    
    @Test
    public void testGetRecordIfExist() {
        Optional<DistroRecord> actual = DistroRecordsHolder.getInstance().getRecordIfExist("testGetRecordIfExist");
        assertFalse(actual.isPresent());
        DistroRecordsHolder.getInstance().getRecord("testGetRecordIfExist");
        actual = DistroRecordsHolder.getInstance().getRecordIfExist("testGetRecordIfExist");
        assertTrue(actual.isPresent());
    }
    
    @Test
    public void testGetTotalSyncCount() {
        long expected = DistroRecordsHolder.getInstance().getTotalSyncCount() + 1;
        DistroRecordsHolder.getInstance().getRecord("testGetTotalSyncCount").syncSuccess();
        assertEquals(expected, DistroRecordsHolder.getInstance().getTotalSyncCount());
    }
    
    @Test
    public void testGetSuccessfulSyncCount() {
        long expected = DistroRecordsHolder.getInstance().getSuccessfulSyncCount() + 1;
        DistroRecordsHolder.getInstance().getRecord("testGetSuccessfulSyncCount").syncSuccess();
        assertEquals(expected, DistroRecordsHolder.getInstance().getSuccessfulSyncCount());
    }
    
    @Test
    public void testGetFailedSyncCount() {
        DistroRecordsHolder.getInstance().getRecord("testGetFailedSyncCount");
        Optional<DistroRecord> actual = DistroRecordsHolder.getInstance().getRecordIfExist("testGetFailedSyncCount");
        assertTrue(actual.isPresent());
        assertEquals(0, DistroRecordsHolder.getInstance().getFailedSyncCount());
        actual.get().syncFail();
        assertEquals(1, DistroRecordsHolder.getInstance().getFailedSyncCount());
        
    }
    
    @Test
    public void testGetFailedVerifyCount() {
        DistroRecordsHolder.getInstance().getRecord("testGetFailedVerifyCount");
        Optional<DistroRecord> actual = DistroRecordsHolder.getInstance().getRecordIfExist("testGetFailedVerifyCount");
        assertTrue(actual.isPresent());
        assertEquals(0, DistroRecordsHolder.getInstance().getFailedVerifyCount());
        actual.get().verifyFail();
        assertEquals(1, DistroRecordsHolder.getInstance().getFailedVerifyCount());
    }
}
