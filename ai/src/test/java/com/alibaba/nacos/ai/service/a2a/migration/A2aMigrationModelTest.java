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

package com.alibaba.nacos.ai.service.a2a.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aMigrationModelTest {
    
    @Test
    void markerShouldValidateEveryRequiredFieldAndTransition() {
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("generation", true,
            "nacos_config", 10L);
        assertTrue(marker.isValid());
        assertEquals(A2aMigrationMarker.SCHEMA_VERSION, marker.getSchemaVersion());
        assertEquals(A2aMigrationState.SYNCING, marker.getState());
        assertEquals("generation", marker.getGeneration());
        assertTrue(marker.isLegacyNamingShadow());
        assertEquals("nacos_config", marker.getStorageProvider());
        assertEquals(10L, marker.getStartedAt());
        assertEquals(10L, marker.getUpdatedAt());
        assertNull(marker.getCompletedAt());
        A2aMigrationMarker quiescing = marker.transition(A2aMigrationState.QUIESCING, "next", 20L);
        assertTrue(quiescing.isValid());
        A2aMigrationMarker canonical =
            quiescing.transition(A2aMigrationState.CANONICAL, "next", 30L);
        assertTrue(canonical.isValid());
        assertEquals(30L, canonical.getCompletedAt());
        
        marker.setSchemaVersion(0);
        assertFalse(marker.isValid());
        marker.setSchemaVersion(A2aMigrationMarker.SCHEMA_VERSION);
        marker.setState(null);
        assertFalse(marker.isValid());
        marker.setState(A2aMigrationState.SYNCING);
        marker.setStorageProvider(" ");
        assertFalse(marker.isValid());
        marker.setStorageProvider("nacos_config");
        marker.setGeneration(" ");
        assertFalse(marker.isValid());
        marker.setGeneration("generation");
        marker.setStartedAt(0L);
        assertFalse(marker.isValid());
        marker.setStartedAt(20L);
        marker.setUpdatedAt(10L);
        assertFalse(marker.isValid());
        marker.setStartedAt(10L);
        marker.setUpdatedAt(10L);
        marker.setCompletedAt(10L);
        assertFalse(marker.isValid());
        marker.setState(A2aMigrationState.CANONICAL);
        marker.setCompletedAt(null);
        assertFalse(marker.isValid());
    }
    
    @Test
    void leaseRecordShouldValidateAndExposeFields() {
        A2aMigrationLeaseRecord lease = A2aMigrationLeaseRecord.of("owner", 100L);
        assertTrue(lease.isValid());
        assertEquals(A2aMigrationLeaseRecord.SCHEMA_VERSION, lease.getSchemaVersion());
        assertEquals("owner", lease.getOwner());
        assertEquals(100L, lease.getExpiresAt());
        lease.setSchemaVersion(0);
        assertFalse(lease.isValid());
        lease.setSchemaVersion(A2aMigrationLeaseRecord.SCHEMA_VERSION);
        lease.setOwner(" ");
        assertFalse(lease.isValid());
        lease.setOwner("owner");
        lease.setExpiresAt(-1L);
        assertFalse(lease.isValid());
    }
    
    @Test
    void progressShouldRemainBoundedAndExposeCounters() {
        A2aMigrationProgress progress = new A2aMigrationProgress();
        progress.setSchemaVersion(1);
        progress.setState(A2aMigrationState.SYNCING);
        progress.setGeneration("generation");
        progress.setUpdatedAt(10L);
        progress.setCursor("c".repeat(600));
        progress.setScanned(11L);
        progress.setMigrated(12L);
        progress.setConflicts(13L);
        progress.setFailed(14L);
        progress.setLastError("e".repeat(2200));
        assertEquals(1, progress.getSchemaVersion());
        assertEquals(A2aMigrationState.SYNCING, progress.getState());
        assertEquals("generation", progress.getGeneration());
        assertEquals(10L, progress.getUpdatedAt());
        assertEquals(512, progress.getCursor().length());
        assertEquals(11L, progress.getScanned());
        assertEquals(12L, progress.getMigrated());
        assertEquals(13L, progress.getConflicts());
        assertEquals(14L, progress.getFailed());
        assertEquals(2048, progress.getLastError().length());
        progress.setCursor(null);
        progress.setLastError(null);
        assertNull(progress.getCursor());
        assertNull(progress.getLastError());
    }
}
