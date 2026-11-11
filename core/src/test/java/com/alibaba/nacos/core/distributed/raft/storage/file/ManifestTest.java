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

package com.alibaba.nacos.core.distributed.raft.storage.file;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestTest {
    
    @Test
    void supportsAnEmptyRangeStartingAfterOne() {
        UUID storeId = UUID.randomUUID();
        Manifest manifest = Manifest.empty(storeId, "group", 101);
        
        assertEquals(FileLogFormat.VERSION, manifest.getFormatVersion());
        assertEquals(storeId, manifest.getStoreId());
        assertEquals("group", manifest.getGroupId());
        assertEquals(101, manifest.getFirstLogIndex());
        assertEquals(100, manifest.getLastLogIndex());
        assertEquals(List.of(), manifest.getSegments());
    }
    
    @Test
    void defensivelyCopiesSegmentMetadata() {
        List<Manifest.SegmentMeta> source = new ArrayList<>();
        source.add(new Manifest.SegmentMeta(1, 10, 11, 48, 200));
        Manifest manifest =
            new Manifest(FileLogFormat.VERSION, UUID.randomUUID(), "group", 10, 11, source);
        source.clear();
        
        assertEquals(1, manifest.getSegments().size());
        assertThrows(UnsupportedOperationException.class,
            () -> manifest.getSegments().add(new Manifest.SegmentMeta(2, 12, 12, 48, 100)));
        assertNotSame(source, manifest.getSegments());
    }
    
    @Test
    void implementsValueEquality() {
        UUID storeId = UUID.randomUUID();
        Manifest.SegmentMeta first = new Manifest.SegmentMeta(1, 10, 11, 48, 200);
        Manifest.SegmentMeta second = new Manifest.SegmentMeta(1, 10, 11, 48, 200);
        Manifest left =
            new Manifest(FileLogFormat.VERSION, storeId, "group", 10, 11, List.of(first));
        Manifest right =
            new Manifest(FileLogFormat.VERSION, storeId, "group", 10, 11, List.of(second));
        
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
