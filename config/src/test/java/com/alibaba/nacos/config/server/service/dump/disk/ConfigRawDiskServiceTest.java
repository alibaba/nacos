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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigRawDiskServiceTest {
    
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
        assertThrows(NacosRuntimeException.class, () -> ConfigRawDiskService.targetFile("../aaa", "testG", "testNS"));
        assertThrows(NacosRuntimeException.class, () -> ConfigRawDiskService.targetFile("testD", "../aaa", "testNS"));
        assertThrows(NacosRuntimeException.class, () -> ConfigRawDiskService.targetFile("testD", "testG", "../aaa"));
    }
    
    /**
     * 测试获取beta文件路径.
     */
    @Test
    void testTargetBetaFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetBetaFile", String.class, String.class,
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
    void testTargetBetaFileWithInvalidParam() throws NoSuchMethodException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetBetaFile", String.class, String.class,
                String.class);
        method.setAccessible(true);
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "../aaa", "testG", "testNS"));
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "testD", "../aaa", "testNS"));
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "testD", "testG", "../aaa"));
    }
    
    /**
     * 测试获取tag文件路径.
     *
     * @throws NoSuchMethodException     方法不存在异常
     * @throws IllegalAccessException    非法访问异常
     * @throws InvocationTargetException 目标异常
     */
    @Test
    void testTargetTagFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetTagFile", String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "aaaa-dsaknkf", "aaaa.dsaknkf", "aaaa:dsaknkf", "aaaa_dsaknkf");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        Path greatGrandParent = grandParent.getParent();
        // 获取最后四段路径
        String secondLastSegment = parent.getFileName().toString();
        String thirdLastSegment = grandParent.getFileName().toString();
        String fourthLastSegment = greatGrandParent.getFileName().toString();
        assertEquals(isWindows() ? "aaaa-dsaknkf" : fourthLastSegment, fourthLastSegment);
        assertEquals(isWindows() ? "aaaa.dsaknkf" : thirdLastSegment, thirdLastSegment);
        assertEquals(isWindows() ? "aaaa%A5%dsaknkf" : secondLastSegment, secondLastSegment);
        String lastSegment = path.getFileName().toString();
        assertEquals("aaaa_dsaknkf", lastSegment);
    }
    
    @Test
    void testTargetTagFileWithInvalidParam() throws NoSuchMethodException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetTagFile", String.class, String.class,
                String.class, String.class);
        method.setAccessible(true);
        assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, "../aaa", "testG", "testNS", "testTag"));
        assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, "testD", "../aaa", "testNS", "testTag"));
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "testD", "testG", "../aaa", "testTag"));
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "testD", "testG", "testNS", "../aaa"));
    }
}
