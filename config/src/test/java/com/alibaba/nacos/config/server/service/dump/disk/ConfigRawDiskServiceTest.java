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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.pathencoder.PathEncoderManager;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRawDiskServiceTest {
    
    @TempDir
    File tempDir;
    
    private String cachedOsName;
    
    @BeforeEach
    void setUp() {
        cachedOsName = System.getProperty("os.name");
    }
    
    private boolean isWindows() {
        return cachedOsName.toLowerCase().startsWith("win");
    }
    
    /**
     * 测试获取文件路径.
     */
    @Test
    void testTargetFile() {
        String dataId = "aaaa-dsaknkf";
        String group = "aaaa.dsaknkf";
        String tenant = "aaaa:dsaknkf";
        File result = ConfigRawDiskService.targetFile(dataId, group, tenant);
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        assertEquals(dataId, path.getFileName().toString());
        assertEquals(group, parent.getFileName().toString());
        assertEquals(isWindows() ? "aaaa%A3%dsaknkf" : tenant,
            grandParent.getFileName().toString());
    }
    
    @Test
    void testTargetFileWithInvalidParam() {
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("../aaa", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("..\\aaa", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "../aaa", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "testG", "../aaa"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile(".", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", ".", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "testG", "."));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("..", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "..", "testNS"));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile("testD", "testG", ".."));
        assertThrows(NacosRuntimeException.class,
            () -> ConfigRawDiskService.targetFile(" dataId", "testG", "testNS"));
    }
    
    @Test
    void testTargetFileReportsRejectedParameter() {
        ConfigDiskPathException exception = assertThrows(ConfigDiskPathException.class,
            () -> ConfigRawDiskService.targetFile("dataId", "..", "namespace"));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertTrue(exception.getMessage().contains("group='..'"));
    }
    
    @Test
    void testTargetFileSanitizesRejectedParameterForLog() {
        ConfigDiskPathException exception = assertThrows(ConfigDiskPathException.class,
            () -> ConfigRawDiskService.targetFile("bad\nname", "group", "namespace"));
        
        assertTrue(exception.getMessage().contains("dataId='bad?name'"));
        assertFalse(exception.getMessage().contains("\n"));
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
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", ".");
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", "..");
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", " gray");
    }
    
    private void assertInvalidTargetGrayFile(Method method, String dataId, String group,
        String tenant, String grayName) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
            () -> method.invoke(null, dataId, group, tenant, grayName));
        assertTrue(exception.getCause() instanceof NacosRuntimeException);
    }
    
    @Test
    void testTargetFileWithUnsafeEncodedParam() {
        PathEncoderManager pathEncoderManager = Mockito.mock(PathEncoderManager.class);
        Mockito.when(pathEncoderManager.encode(Mockito.anyString()))
            .thenAnswer(invocation -> "group".equals(invocation.getArgument(0)) ? ".."
                : invocation.getArgument(0));
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<PathEncoderManager> pathEncoderMock =
                Mockito.mockStatic(PathEncoderManager.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            pathEncoderMock.when(PathEncoderManager::getInstance).thenReturn(pathEncoderManager);
            assertThrows(NacosRuntimeException.class,
                () -> ConfigRawDiskService.targetFile("dataId", "group", "tenant"));
        }
    }
    
    @Test
    void testRemoveConfigInfoRejectsParentDirectoryTraversal() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            File marker = new File(tempDir, "marker");
            FileUtils.writeStringToFile(marker, "marker", "UTF-8");
            ConfigRawDiskService service = new ConfigRawDiskService();
            assertThrows(NacosRuntimeException.class,
                () -> service.removeConfigInfo("..", "..", ""));
            assertTrue(marker.isFile());
        }
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
            service.saveToDisk("application.properties", "group.v1", "myTenant.v1",
                "tenant content");
            assertEquals("tenant content",
                service.getContent("application.properties", "group.v1", "myTenant.v1"));
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
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        Path greatGrandParent = grandParent.getParent();
        assertEquals("graynem4567", path.getFileName().toString());
        assertEquals("data345678", parent.getFileName().toString());
        assertEquals("group3456", grandParent.getFileName().toString());
        assertEquals("tenant1234", greatGrandParent.getFileName().toString());
    }
    
}
