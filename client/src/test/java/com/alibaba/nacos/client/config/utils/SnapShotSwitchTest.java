/*
 *
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
 *
 */

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;

class SnapShotSwitchTest {
    
    private MockedStatic<LocalConfigInfoProcessor> localConfigInfoProcessor;
    
    private String originalSnapshotEnabled;
    
    private Boolean originalIsSnapShot;
    
    @BeforeEach
    void setUp() {
        localConfigInfoProcessor = Mockito.mockStatic(LocalConfigInfoProcessor.class);
        originalSnapshotEnabled = System.getProperty("configSnapshotEnabled");
        originalIsSnapShot = SnapShotSwitch.getIsSnapShot();
        System.clearProperty("configSnapshotEnabled");
        SnapShotSwitch.setIsSnapShot(true);
        // Resetting the switch also requests cleanup.
        localConfigInfoProcessor.clearInvocations();
    }
    
    @AfterEach
    void tearDown() {
        if (originalSnapshotEnabled == null) {
            System.clearProperty("configSnapshotEnabled");
        } else {
            System.setProperty("configSnapshotEnabled", originalSnapshotEnabled);
        }
        SnapShotSwitch.setIsSnapShot(originalIsSnapShot);
        localConfigInfoProcessor.close();
    }
    
    @Test
    void testGetIsSnapShot() {
        Boolean isSnapShot = SnapShotSwitch.getIsSnapShot();
        assertTrue(isSnapShot);
        
        SnapShotSwitch.setIsSnapShot(false);
        assertFalse(SnapShotSwitch.getIsSnapShot());
        
        SnapShotSwitch.setIsSnapShot(true);
        assertTrue(SnapShotSwitch.getIsSnapShot());
    }
    
    @Test
    void testInitSnapshotSwitchFromClientProperty() {
        System.setProperty("configSnapshotEnabled", "false");
        SnapShotSwitch.initSnapshotSwitch();
        assertFalse(SnapShotSwitch.getIsSnapShot());
        
        System.setProperty("configSnapshotEnabled", "true");
        SnapShotSwitch.initSnapshotSwitch();
        assertTrue(SnapShotSwitch.getIsSnapShot());
    }
    
    @Test
    void testInitSnapshotSwitchUsesTrueWhenPropertyAbsent() {
        SnapShotSwitch.initSnapshotSwitch();
        assertTrue(SnapShotSwitch.getIsSnapShot());
        localConfigInfoProcessor.verify(LocalConfigInfoProcessor::cleanAllSnapshot, never());
    }
    
    @Test
    void testInitSnapshotSwitchDisabledRequestsCleanup() {
        System.setProperty("configSnapshotEnabled", "false");
        SnapShotSwitch.initSnapshotSwitch();
        assertFalse(SnapShotSwitch.getIsSnapShot());
        localConfigInfoProcessor.verify(LocalConfigInfoProcessor::cleanAllSnapshot);
    }
    
    @Test
    void testInitSnapshotSwitchEnabledDoesNotRequestCleanup() {
        System.setProperty("configSnapshotEnabled", "true");
        SnapShotSwitch.initSnapshotSwitch();
        assertTrue(SnapShotSwitch.getIsSnapShot());
        localConfigInfoProcessor.verify(LocalConfigInfoProcessor::cleanAllSnapshot, never());
    }
    
    @Test
    void testInitSnapshotSwitchKeepsSnapshotOffWhenCleanupThrowsRuntimeException() {
        System.setProperty("configSnapshotEnabled", "false");
        localConfigInfoProcessor.when(LocalConfigInfoProcessor::cleanAllSnapshot)
            .thenThrow(new IllegalStateException("simulated snapshot cleanup failure"));
        try {
            assertDoesNotThrow(SnapShotSwitch::initSnapshotSwitch);
            assertFalse(SnapShotSwitch.getIsSnapShot());
            localConfigInfoProcessor.verify(LocalConfigInfoProcessor::cleanAllSnapshot);
        } finally {
            // Reset the failure stub so tearDown can request the setter cleanup normally.
            localConfigInfoProcessor.reset();
        }
    }
    
    @Test
    void testInitSnapshotSwitchKeepsSnapshotOffWhenCleanupThrowsLinkageError() {
        System.setProperty("configSnapshotEnabled", "false");
        localConfigInfoProcessor.when(LocalConfigInfoProcessor::cleanAllSnapshot)
            .thenThrow(new ExceptionInInitializerError("simulated cleanup linkage failure"));
        try {
            assertDoesNotThrow(SnapShotSwitch::initSnapshotSwitch);
            assertFalse(SnapShotSwitch.getIsSnapShot());
            localConfigInfoProcessor.verify(LocalConfigInfoProcessor::cleanAllSnapshot);
        } finally {
            localConfigInfoProcessor.reset();
        }
    }
    
    @Test
    void testInitSnapshotSwitchPropagatesFatalVmErrorFromCleanup() {
        System.setProperty("configSnapshotEnabled", "false");
        localConfigInfoProcessor.when(LocalConfigInfoProcessor::cleanAllSnapshot)
            .thenThrow(new OutOfMemoryError("simulated fatal cleanup error"));
        try {
            assertThrows(OutOfMemoryError.class, SnapShotSwitch::initSnapshotSwitch);
        } finally {
            localConfigInfoProcessor.reset();
        }
    }
    
}
