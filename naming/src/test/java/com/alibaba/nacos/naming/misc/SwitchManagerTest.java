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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwitchManagerTest {
    
    private SwitchManager switchManager;
    
    private SwitchDomain switchDomain;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    @BeforeEach
    void setUp() {
        switchDomain = new SwitchDomain();
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        switchManager = new SwitchManager(switchDomain, protocolManager);
    }
    
    @Test
    void testUpdatePushVersionWithValidJavaValue() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "java:2.0.0", true);
        assertEquals("2.0.0", switchDomain.getPushVersionOfJava());
    }
    
    @Test
    void testUpdatePushVersionWithValidPythonValue() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "python:3.1.0", true);
        assertEquals("3.1.0", switchDomain.getPushVersionOfPython());
    }
    
    @Test
    void testUpdatePushVersionWithMultipleColons() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "java:1.0.0:extra", true);
        assertEquals("1.0.0", switchDomain.getPushVersionOfJava());
    }
    
    @Test
    void testUpdatePushVersionWithoutDelimiter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "invalidvalue", true));
        assertEquals("illegal format, must be 'type:version', but got: invalidvalue",
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithEmptyValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "", true));
        assertEquals("illegal format, must be 'type:version', but got: ", exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithTrailingColon() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "java:", true));
        assertEquals("illegal format, must be 'type:version', but got: java:",
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithInvalidVersionFormat() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "java:abc", true));
        assertEquals("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX,
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithUnsupportedClientType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "ruby:1.0.0", true));
        assertEquals("unsupported client type: ruby", exception.getMessage());
    }
    
    @Test
    void testUpdateEnableStandaloneToFalseAppliesValue() throws Exception {
        assertTrue(switchDomain.isEnableStandalone());
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "false", true);
        assertFalse(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateEnableStandaloneToTrueAppliesValue() throws Exception {
        switchDomain.setEnableStandalone(false);
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "true", true);
        assertTrue(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateEnableStandaloneWithEmptyValueIsNoOp() throws Exception {
        assertTrue(switchDomain.isEnableStandalone());
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "", true);
        assertTrue(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateHealthCheckEnabledWithLegacyCheckEntry() throws Exception {
        assertTrue(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.CHECK, "false", true);
        assertFalse(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.CHECK, "true", true);
        assertTrue(switchDomain.isHealthCheckEnabled());
    }
    
    @Test
    void testUpdateHealthCheckEnabledWithExposedFieldName() throws Exception {
        assertTrue(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.HEALTH_CHECK_ENABLED, "false", true);
        assertFalse(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.HEALTH_CHECK_ENABLED, "true", true);
        assertTrue(switchDomain.isHealthCheckEnabled());
    }
    
    @Test
    void testHealthCheckEnabledEntryConstantValue() {
        assertEquals("healthCheckEnabled", SwitchEntry.HEALTH_CHECK_ENABLED);
    }
}
