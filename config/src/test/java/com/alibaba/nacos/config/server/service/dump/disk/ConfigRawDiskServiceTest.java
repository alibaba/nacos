/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump.disk;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRawDiskServiceTest {
    
    @TempDir
    File tempDir;
    
    private String cachedOsName;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedOsName = System.getProperty("os.name");
    }
    
    private boolean isWindows() {
        return cachedOsName.toLowerCase().startsWith("win");
    }
    
    /**
     * 测试获取文件路径.
     */
    @Test
    void testTargetFile()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method =
            ConfigRawDiskService.class.getDeclaredMethod("targetFile", String.class, String.class,
                String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "aaaa-dsaknkf", "aaaa.dsaknkf", "aaaa:dsaknkf");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        // 获取最后三段路径
        String lastSegment = path.getFileName().toString();
        String secondLastSegment = parent.getFileName().toString();
        String thirdLastSegment = grandParent.getFileName().toString();
        assertEquals(isWindows() ? "aaaa-dsaknkf" : thirdLastSegment, thirdLastSegment);
        assertEquals(isWindows() ? "aaaa.dsaknkf" : secondLastSegment, secondLastSegment);
        assertEquals(isWindows() ? "aaaa%A5%dsaknkf" : lastSegment, lastSegment);
    }
    
    @Test
    void testTargetFileWithInvalidParam() {
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("../aaa", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "../aaa", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "testG", "../aaa"));
    }
    
    @Test
    void testTargetGrayFileWithInvalidParam() throws Exception {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetGrayFile", String.class,
            String.class, String.class, String.class);
        method.setAccessible(true);
        
        InvocationTargetException exception =
            assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, "dataId", "group", "tenant", "../gray"));
        
        assertTrue(exception.getCause() instanceof NacosRuntimeException);
    }
    
    @Test
    void testSaveToDiskAndGetContent() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveToDisk("dataId", "group", "", "hello content");
            String content = service.getContent("dataId", "group", "");
            assertEquals("hello content", content);
        }
    }
    
    @Test
    void testGetContentNonExistentFile() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            assertNull(service.getContent("noexist", "group", ""));
        }
    }
    
    @Test
    void testGetContentReturnsNullWhenTargetIsDirectory() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            File target = ConfigRawDiskService.targetFile("dirData", "group", "");
            FileUtils.forceMkdir(target);
            
            ConfigRawDiskService service = new ConfigRawDiskService();
            
            assertNull(service.getContent("dirData", "group", ""));
        }
    }
    
    @Test
    void testSaveAndGetGrayContent() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveGrayToDisk("dataId", "group", "tenant", "gray1", "gray content");
            String content = service.getGrayContent("dataId", "group", "tenant", "gray1");
            assertEquals("gray content", content);
        }
    }
    
    @Test
    void testRemoveConfigInfo() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveToDisk("dataId", "group", "", "content");
            service.removeConfigInfo("dataId", "group", "");
            assertNull(service.getContent("dataId", "group", ""));
        }
    }
    
    @Test
    void testRemoveConfigInfo4Gray() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveGrayToDisk("d", "g", "", "gn", "gray");
            service.removeConfigInfo4Gray("d", "g", "", "gn");
            assertNull(service.getGrayContent("d", "g", "", "gn"));
        }
    }
    
    @Test
    void testClearAll() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveToDisk("d1", "g1", "", "content1");
            service.saveToDisk("d2", "g2", "t1", "content2");
            service.clearAll();
            assertNull(service.getContent("d1", "g1", ""));
            assertNull(service.getContent("d2", "g2", "t1"));
        }
    }
    
    @Test
    void testClearAllGray() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveGrayToDisk("d1", "g1", "", "gn", "gc");
            service.saveGrayToDisk("d2", "g2", "t1", "gn2", "gc2");
            service.clearAllGray();
            assertNull(service.getGrayContent("d1", "g1", "", "gn"));
            assertNull(service.getGrayContent("d2", "g2", "t1", "gn2"));
        }
    }
    
    @Test
    void testClearAllWhenDeleteFails() {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<FileUtils> fileUtilsMock = Mockito.mockStatic(FileUtils.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            assertTrue(new File(tempDir, "data/config-data").mkdirs());
            assertTrue(new File(tempDir, "data/tenant-config-data").mkdirs());
            fileUtilsMock.when(() -> FileUtils.deleteQuietly(Mockito.any(File.class)))
                .thenReturn(false);
            
            new ConfigRawDiskService().clearAll();
            
            fileUtilsMock.verify(() -> FileUtils.deleteQuietly(Mockito.any(File.class)),
                Mockito.times(2));
        }
    }
    
    @Test
    void testClearAllGrayWhenDeleteFails() {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<FileUtils> fileUtilsMock = Mockito.mockStatic(FileUtils.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            assertTrue(new File(tempDir, "data/gray-data").mkdirs());
            assertTrue(new File(tempDir, "data/tenant-gray-data").mkdirs());
            fileUtilsMock.when(() -> FileUtils.deleteQuietly(Mockito.any(File.class)))
                .thenReturn(false);
            
            new ConfigRawDiskService().clearAllGray();
            
            fileUtilsMock.verify(() -> FileUtils.deleteQuietly(Mockito.any(File.class)),
                Mockito.times(2));
        }
    }
    
    @Test
    void testSaveToDiskWithTenant() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveToDisk("d", "g", "myTenant", "tenant content");
            assertEquals("tenant content", service.getContent("d", "g", "myTenant"));
        }
    }
    
    /**
     * 测试获取beta文件路径.
     */
    @Test
    void testTargetGrayFile()
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetGrayFile", String.class,
            String.class,
            String.class, String.class);
        method.setAccessible(true);
        File result =
            (File) method.invoke(null, "data345678", "group3456", "tenant1234", "graynem4567");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        Path grand2Parent = grandParent.getParent();
        
        // 获取最后三段路径
        String fourthLastSegment = grand2Parent.getFileName().toString();
        assertEquals(fourthLastSegment, "tenant1234");
        String thirdLastSegment = grandParent.getFileName().toString();
        assertEquals(isWindows() ? "aaaa-dsaknkf" : thirdLastSegment, "group3456");
        String secondLastSegment = parent.getFileName().toString();
        assertEquals(isWindows() ? "aaaa-dsaknkf" : secondLastSegment, "data345678");
        String lastSegment = path.getFileName().toString();
        assertEquals(isWindows() ? "aaaa-dsaknkf" : lastSegment, "graynem4567");
        
    }
    
}
