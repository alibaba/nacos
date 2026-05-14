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

class LocalConfigInfoProcessorTest {
    
    private String envName;
    
    @BeforeEach
    void setUp() {
        envName = "envtest-" + UUID.randomUUID();
        SnapShotSwitch.setIsSnapShot(true);
    }
    
    @AfterEach
    void tearDown() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
        SnapShotSwitch.setIsSnapShot(true);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new LocalConfigInfoProcessor());
    }
    
    @Test
    void testGetFailoverNoFile() {
        assertNull(LocalConfigInfoProcessor.getFailover(envName, "d", "g", null));
    }
    
    @Test
    void testGetFailoverWithFile() throws Exception {
        File f = LocalConfigInfoProcessor.getFailoverFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "failover-content".getBytes(StandardCharsets.UTF_8));
        assertEquals("failover-content",
            LocalConfigInfoProcessor.getFailover(envName, "d", "g", null));
    }
    
    @Test
    void testGetFailoverWithTenant() throws Exception {
        File f = LocalConfigInfoProcessor.getFailoverFile(envName, "d", "g", "tenant1");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "failover-tenant".getBytes(StandardCharsets.UTF_8));
        assertEquals("failover-tenant",
            LocalConfigInfoProcessor.getFailover(envName, "d", "g", "tenant1"));
    }
    
    @Test
    void testGetSnapshotSnapShotSwitchOff() {
        SnapShotSwitch.setIsSnapShot(false);
        assertNull(LocalConfigInfoProcessor.getSnapshot(envName, "d", "g", null));
    }
    
    @Test
    void testGetSnapshotNoFile() {
        assertNull(LocalConfigInfoProcessor.getSnapshot(envName, "d", "g", null));
    }
    
    @Test
    void testGetSnapshotWithFile() throws Exception {
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "snap-content".getBytes(StandardCharsets.UTF_8));
        assertEquals("snap-content",
            LocalConfigInfoProcessor.getSnapshot(envName, "d", "g", null));
    }
    
    @Test
    void testGetSnapshotWithTenant() throws Exception {
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", "tenant1");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "snap-tenant".getBytes(StandardCharsets.UTF_8));
        assertEquals("snap-tenant",
            LocalConfigInfoProcessor.getSnapshot(envName, "d", "g", "tenant1"));
    }
    
    @Test
    void testReadFileMultiInstance() throws Exception {
        File f = File.createTempFile("nacos-cfg-multi-", ".tmp");
        f.deleteOnExit();
        Files.write(f.toPath(), "multi".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            try (MockedStatic<ConcurrentDiskUtil> diskMock =
                Mockito.mockStatic(ConcurrentDiskUtil.class)) {
                diskMock.when(() -> ConcurrentDiskUtil.getFileContent(Mockito.any(File.class),
                    Mockito.anyString())).thenReturn("multi-via-cdu");
                Method m = LocalConfigInfoProcessor.class.getDeclaredMethod("readFile",
                    File.class);
                m.setAccessible(true);
                assertEquals("multi-via-cdu", m.invoke(null, f));
            }
        }
    }
    
    @Test
    void testReadFileNonExistReturnsNull() throws Exception {
        Method m = LocalConfigInfoProcessor.class.getDeclaredMethod("readFile", File.class);
        m.setAccessible(true);
        File missing = new File("/non/existent/path/nacos-test-" + UUID.randomUUID());
        assertNull(m.invoke(null, missing));
    }
    
    @Test
    void testSaveSnapshotSwitchOff() {
        SnapShotSwitch.setIsSnapShot(false);
        LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, "x");
        // file should not be written
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        assertTrue(!f.exists());
    }
    
    @Test
    void testSaveSnapshotWithContent() {
        LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, "save-me");
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        assertTrue(f.exists());
    }
    
    @Test
    void testSaveSnapshotMultiInstance() {
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, "save-multi");
            File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
            assertTrue(f.exists());
        }
    }
    
    @Test
    void testSaveSnapshotWithNullDeletes() throws Exception {
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(f.exists());
        LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, null);
        assertTrue(!f.exists());
    }
    
    @Test
    void testCleanAllSnapshot() throws Exception {
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(f.exists());
        LocalConfigInfoProcessor.cleanAllSnapshot();
        assertTrue(!f.exists());
    }
    
    @Test
    void testCleanEnvSnapshot() throws Exception {
        File f = new File(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH,
            envName + "_nacos/snapshot/test.txt");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        LocalConfigInfoProcessor.cleanEnvSnapshot(envName);
        assertTrue(!f.exists());
    }
    
    @Test
    void testGetFailoverIoExceptionReturnsNull() throws Exception {
        File f = LocalConfigInfoProcessor.getFailoverFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            try (MockedStatic<ConcurrentDiskUtil> cd =
                Mockito.mockStatic(ConcurrentDiskUtil.class)) {
                cd.when(() -> ConcurrentDiskUtil.getFileContent(Mockito.any(File.class),
                    Mockito.anyString())).thenThrow(new IOException("forced"));
                assertNull(LocalConfigInfoProcessor.getFailover(envName, "d", "g", null));
            }
        }
    }
    
    @Test
    void testGetSnapshotIoExceptionReturnsNull() throws Exception {
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<JvmUtil> jvmMock = Mockito.mockStatic(JvmUtil.class)) {
            jvmMock.when(JvmUtil::isMultiInstance).thenReturn(true);
            try (MockedStatic<ConcurrentDiskUtil> cd =
                Mockito.mockStatic(ConcurrentDiskUtil.class)) {
                cd.when(() -> ConcurrentDiskUtil.getFileContent(Mockito.any(File.class),
                    Mockito.anyString())).thenThrow(new IOException("forced"));
                assertNull(LocalConfigInfoProcessor.getSnapshot(envName, "d", "g", null));
            }
        }
    }
    
    @Test
    void testSaveSnapshotDeleteIoExceptionSwallowed() throws Exception {
        // Pre-create file
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock.when(() -> IoUtils.delete(Mockito.any(File.class)))
                .thenThrow(new IOException("forced"));
            // Should swallow the IOException
            LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, null);
        }
    }
    
    @Test
    void testSaveSnapshotWriteIoExceptionSwallowed() throws Exception {
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock
                .when(() -> IoUtils.writeStringToFile(Mockito.any(File.class), Mockito.anyString(),
                    Mockito.anyString()))
                .thenThrow(new IOException("forced"));
            // Should swallow the IOException
            LocalConfigInfoProcessor.saveSnapshot(envName, "d", "g", null, "content");
        }
    }
    
    @Test
    void testCleanAllSnapshotIoExceptionSwallowed() throws Exception {
        // Pre-create a snapshot directory ending with SUFFIX so listFiles is non-empty
        File f = LocalConfigInfoProcessor.getSnapshotFile(envName, "d", "g", null);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock.when(() -> IoUtils.cleanDirectory(Mockito.any(File.class)))
                .thenThrow(new IOException("forced"));
            // Should swallow the IOException
            LocalConfigInfoProcessor.cleanAllSnapshot();
        }
    }
    
    @Test
    void testCleanEnvSnapshotIoExceptionSwallowed() throws Exception {
        try (MockedStatic<IoUtils> ioMock = Mockito.mockStatic(IoUtils.class)) {
            ioMock.when(() -> IoUtils.cleanDirectory(Mockito.any(File.class)))
                .thenThrow(new IOException("forced"));
            // Should swallow the IOException
            LocalConfigInfoProcessor.cleanEnvSnapshot(envName);
        }
    }
}
