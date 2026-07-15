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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.client.config.utils.JvmUtil;
import com.alibaba.nacos.client.config.utils.SnapShotSwitch;
import com.alibaba.nacos.client.utils.ConcurrentDiskUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalEncryptedDataKeyProcessorTest {
    
    private String envName;
    
    @BeforeEach
    void setUp() {
        envName = "envenc-" + UUID.randomUUID();
        SnapShotSwitch.setIsSnapShot(true);
    }
    
    @AfterEach
    void tearDown() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
        SnapShotSwitch.setIsSnapShot(true);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new LocalEncryptedDataKeyProcessor());
    }
    
    private File invokePrivateGetFailoverFile(String env, String dataId, String group,
        String tenant) throws Exception {
        Method m = LocalEncryptedDataKeyProcessor.class.getDeclaredMethod(
            "getEncryptDataKeyFailoverFile", String.class, String.class, String.class,
            String.class);
        m.setAccessible(true);
        return (File) m.invoke(null, env, dataId, group, tenant);
    }
    
    private File invokePrivateGetSnapshotFile(String env, String dataId, String group,
        String tenant) throws Exception {
        Method m = LocalEncryptedDataKeyProcessor.class.getDeclaredMethod(
            "getEncryptDataKeySnapshotFile", String.class, String.class, String.class,
            String.class);
        m.setAccessible(true);
        return (File) m.invoke(null, env, dataId, group, tenant);
    }
    
    @Test
    void testGetEncryptDataKeyFailoverNoFile() {
        assertNull(LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, "d", "g",
            null));
    }
    
    @Test
    void testGetEncryptDataKeyFailoverWithFile() throws Exception {
        File f = invokePrivateGetFailoverFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "ek-failover".getBytes(StandardCharsets.UTF_8));
        assertEquals("ek-failover",
            LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, "d", "g", null));
    }
    
    @Test
    void testGetEncryptDataKeyFailoverWithTenant() throws Exception {
        File f = invokePrivateGetFailoverFile(envName, "d", "g", "tenantA");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "ek-failover-t".getBytes(StandardCharsets.UTF_8));
        assertEquals("ek-failover-t",
            LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, "d", "g",
                "tenantA"));
    }
    
    @Test
    void testGetEncryptDataKeySnapshotSwitchOff() {
        SnapShotSwitch.setIsSnapShot(false);
        assertNull(LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, "d", "g",
            null));
    }
    
    @Test
    void testGetEncryptDataKeySnapshotNoFile() {
        assertNull(LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, "d", "g",
            null));
    }
    
    @Test
    void testGetEncryptDataKeySnapshotWithFile() throws Exception {
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "ek-snap".getBytes(StandardCharsets.UTF_8));
        assertEquals("ek-snap",
            LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, "d", "g", null));
    }
    
    @Test
    void testGetEncryptDataKeySnapshotWithTenant() throws Exception {
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", "tenantA");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "ek-snap-t".getBytes(StandardCharsets.UTF_8));
        assertEquals("ek-snap-t",
            LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName, "d", "g",
                "tenantA"));
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotSwitchOff() throws Exception {
        SnapShotSwitch.setIsSnapShot(false);
        LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null, "ek");
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        assertTrue(!f.exists());
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotWithContent() throws Exception {
        LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null,
            "save-ek");
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        assertTrue(f.exists());
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotWithContentMultiInstance() throws Exception {
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null,
                "save-multi");
            File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
            assertTrue(f.exists());
        }
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotWithNullDeletes() throws Exception {
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(f.exists());
        LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null, null);
        assertTrue(!f.exists());
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotWithTenant() throws Exception {
        LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", "tenantA",
            "save-with-tenant");
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", "tenantA");
        assertTrue(f.exists());
    }
    
    @Test
    void testGetEncryptDataKeyFailoverIoExceptionReturnsNull() throws Exception {
        File f = invokePrivateGetFailoverFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            try (MockedStatic<ConcurrentDiskUtil> cd =
                Mockito.mockStatic(ConcurrentDiskUtil.class)) {
                cd.when(() -> ConcurrentDiskUtil.getFileContent(Mockito.any(File.class),
                    Mockito.anyString())).thenThrow(new IOException("forced"));
                assertNull(LocalEncryptedDataKeyProcessor.getEncryptDataKeyFailover(envName, "d",
                    "g", null));
            }
        }
    }
    
    @Test
    void testGetEncryptDataKeySnapshotIoExceptionReturnsNull() throws Exception {
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            try (MockedStatic<ConcurrentDiskUtil> cd =
                Mockito.mockStatic(ConcurrentDiskUtil.class)) {
                cd.when(() -> ConcurrentDiskUtil.getFileContent(Mockito.any(File.class),
                    Mockito.anyString())).thenThrow(new IOException("forced"));
                assertNull(LocalEncryptedDataKeyProcessor.getEncryptDataKeySnapshot(envName,
                    "d", "g", null));
            }
        }
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotDeleteIoExceptionSwallowed() throws Exception {
        File f = invokePrivateGetSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock.when(() -> IoUtils.delete(Mockito.any(File.class)))
                .thenThrow(new IOException("forced"));
            // Null content takes the delete branch
            LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null,
                null);
        }
    }
    
    @Test
    void testSaveEncryptDataKeySnapshotWriteIoExceptionSwallowed() throws Exception {
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock
                .when(() -> IoUtils.writeStringToFile(Mockito.any(File.class), Mockito.anyString(),
                    Mockito.anyString()))
                .thenThrow(new IOException("forced"));
            LocalEncryptedDataKeyProcessor.saveEncryptDataKeySnapshot(envName, "d", "g", null,
                "ek-content");
        }
    }
}
