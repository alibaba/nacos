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

package com.alibaba.nacos.common.packagescan;

import com.alibaba.nacos.common.packagescan.mock.AnnotationClass;
import com.alibaba.nacos.common.packagescan.mock.MockClass;
import com.alibaba.nacos.common.packagescan.mock.TestScan;
import com.alibaba.nacos.common.packagescan.resource.PathMatchingResourcePatternResolver;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPackageScanTest {
    
    @Mock
    PathMatchingResourcePatternResolver pathMatchingResourcePatternResolver;
    
    DefaultPackageScan packageScan;
    
    @BeforeEach
    void setUp() throws Exception {
        packageScan = new DefaultPackageScan();
    }
    
    @Test
    void testGetSubTypesOf() {
        packageScan = new DefaultPackageScan();
        Set<Class<MockClass>> subTypesOf = packageScan.getSubTypesOf(AnnotationClass.class.getPackage().getName(),
                MockClass.class);
        assertEquals(3, subTypesOf.size());
    }
    
    @Test
    void testGetTypesAnnotatedWith() {
        packageScan = new DefaultPackageScan();
        Set<Class<Object>> actual = packageScan.getTypesAnnotatedWith(AnnotationClass.class.getPackage().getName(),
                TestScan.class);
        assertEquals(1, actual.size());
        assertEquals(AnnotationClass.class, actual.iterator().next());
    }
    
    @Test
    void testGetSubTypesOfWithException() throws NoSuchFieldException, IllegalAccessException, IOException {
        setResolver();
        String path = AnnotationClass.class.getPackage().getName();
        when(pathMatchingResourcePatternResolver.getResources(anyString())).thenThrow(new IOException("test"));
        Set<Class<MockClass>> subTypesOf = packageScan.getSubTypesOf(path, MockClass.class);
        assertTrue(subTypesOf.isEmpty());
    }
    
    @Test
    void testGetTypesAnnotatedWithException() throws NoSuchFieldException, IllegalAccessException, IOException {
        setResolver();
        String path = AnnotationClass.class.getPackage().getName();
        when(pathMatchingResourcePatternResolver.getResources(anyString())).thenThrow(new IOException("test"));
        Set<Class<Object>> actual = packageScan.getTypesAnnotatedWith(path, TestScan.class);
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void testClassVersionNotMatch() throws NoSuchFieldException, IllegalAccessException, IOException {
        setResolver();
        Resource resource = mock(Resource.class);
        byte[] testCase = new byte[8];
        testCase[7] = (byte) 64;
        InputStream inputStream = new ByteArrayInputStream(testCase);
        when(resource.getInputStream()).thenReturn(inputStream);
        String path = AnnotationClass.class.getPackage().getName();
        when(pathMatchingResourcePatternResolver.getResources(anyString())).thenReturn(new Resource[] {resource});
        Set<Class<MockClass>> subTypesOf = packageScan.getSubTypesOf(path, MockClass.class);
        assertTrue(subTypesOf.isEmpty());
    }
    
    private void setResolver() throws NoSuchFieldException, IllegalAccessException {
        Field field = DefaultPackageScan.class.getDeclaredField("resourcePatternResolver");
        field.setAccessible(true);
        field.set(packageScan, pathMatchingResourcePatternResolver);
    }
}