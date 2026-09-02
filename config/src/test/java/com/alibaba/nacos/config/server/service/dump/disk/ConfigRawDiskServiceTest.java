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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRawDiskServiceTest {
    
    @TempDir
    private File tempDir;
    
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
    void testTargetFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetFile", String.class, String.class,
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
    }
    
    @Test
    void testTargetFileWithUnsafeEncodedParam() {
        PathEncoderManager pathEncoderManager = Mockito.mock(PathEncoderManager.class);
        Mockito.when(pathEncoderManager.encode(Mockito.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        try (MockedStatic<PathEncoderManager> pathEncoderMock = Mockito
                .mockStatic(PathEncoderManager.class)) {
            pathEncoderMock.when(PathEncoderManager::getInstance).thenReturn(pathEncoderManager);
            String[] unsafeEncodedParams = {"", ".", "..", "../target", "..\\target", "\0"};
            for (String unsafeEncodedParam : unsafeEncodedParams) {
                Mockito.when(pathEncoderManager.encode("group")).thenReturn(unsafeEncodedParam);
                assertThrows(NacosRuntimeException.class,
                        () -> ConfigRawDiskService.targetFile("dataId", "group", "tenant"));
            }
        }
    }

    @Test
    void testTargetFileAllowsSafeEncodedParam() {
        PathEncoderManager pathEncoderManager = Mockito.mock(PathEncoderManager.class);
        Mockito.when(pathEncoderManager.encode(Mockito.anyString()))
                .thenAnswer(invocation -> "tenant".equals(invocation.getArgument(0))
                        ? "tenant%A5%encoded" : invocation.getArgument(0));
        try (MockedStatic<PathEncoderManager> pathEncoderMock = Mockito
                .mockStatic(PathEncoderManager.class)) {
            pathEncoderMock.when(PathEncoderManager::getInstance).thenReturn(pathEncoderManager);
            File target = ConfigRawDiskService.targetFile("dataId", "group", "tenant");
            assertEquals("tenant%A5%encoded", target.getParentFile().getParentFile().getName());
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
    void testSafeDiskOperationsRemainCompatible() throws IOException {
        try (MockedStatic<EnvUtil> envUtilMock = Mockito.mockStatic(EnvUtil.class)) {
            envUtilMock.when(EnvUtil::getNacosHome).thenReturn(tempDir.getAbsolutePath());
            ConfigRawDiskService service = new ConfigRawDiskService();
            service.saveToDisk("application.properties", "group.v1", "tenant.v1", "content");
            assertEquals("content",
                    service.getContent("application.properties", "group.v1", "tenant.v1"));
            service.removeConfigInfo("application.properties", "group.v1", "tenant.v1");
            assertNull(service.getContent("application.properties", "group.v1", "tenant.v1"));

            service.saveToDisk("default.properties", "default.group", "", "default content");
            assertEquals("default content",
                    service.getContent("default.properties", "default.group", ""));
            service.removeConfigInfo("default.properties", "default.group", "");
            assertNull(service.getContent("default.properties", "default.group", ""));
            
            service.saveGrayToDisk("application.properties", "group.v1", "tenant.v1", "gray.v1",
                    "gray content");
            assertEquals("gray content",
                    service.getGrayContent("application.properties", "group.v1", "tenant.v1",
                            "gray.v1"));
            service.removeConfigInfo4Gray("application.properties", "group.v1", "tenant.v1",
                    "gray.v1");
            assertNull(service.getGrayContent("application.properties", "group.v1", "tenant.v1",
                    "gray.v1"));

            service.saveGrayToDisk("default.properties", "default.group", "", "default.gray",
                    "default gray content");
            assertEquals("default gray content",
                    service.getGrayContent("default.properties", "default.group", "",
                            "default.gray"));
            service.removeConfigInfo4Gray("default.properties", "default.group", "",
                    "default.gray");
            assertNull(service.getGrayContent("default.properties", "default.group", "",
                    "default.gray"));
        }
    }
    
    /**
     * 测试获取beta文件路径.
     */
    @Test
    void testTargetGrayFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetGrayFile", String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "data345678", "group3456", "tenant1234", "graynem4567");
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
    
    @Test
    void testTargetGrayFileWithInvalidParam() throws Exception {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetGrayFile", String.class,
                String.class, String.class, String.class);
        method.setAccessible(true);
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", "");
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", ".");
        assertInvalidTargetGrayFile(method, "dataId", "group", "tenant", "..");
        assertInvalidTargetGrayFile(method, "dataId", "..", "tenant", "gray");
    }
    
    private void assertInvalidTargetGrayFile(Method method, String dataId, String group,
            String tenant, String grayName) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, dataId, group, tenant, grayName));
        assertTrue(exception.getCause() instanceof NacosRuntimeException);
    }
    
}
