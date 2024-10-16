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

package com.alibaba.nacos.sys.filter;

import com.alibaba.nacos.sys.filter.mock.MockNacosPackageExcludeFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosTypeExcludeFilterTest {
    
    NacosTypeExcludeFilter filter;
    
    Map<String, NacosPackageExcludeFilter> packageExcludeFilters;
    
    @Mock
    MetadataReader metadataReader;
    
    @Mock
    MetadataReaderFactory metadataReaderFactory;
    
    @Mock
    AnnotationMetadata annotationMetadata;
    
    @Mock
    ClassMetadata classMetadata;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        filter = new NacosTypeExcludeFilter();
        packageExcludeFilters = (Map<String, NacosPackageExcludeFilter>) ReflectionTestUtils.getField(filter,
                "packageExcludeFilters");
    }
    
    @Test
    void testMatchWithoutExcludeFilters() throws IOException {
        packageExcludeFilters.clear();
        assertFalse(filter.match(metadataReader, metadataReaderFactory));
    }
    
    @Test
    void testMatchWithSpringBootApplicationAnnotation() throws IOException {
        when(metadataReader.getAnnotationMetadata()).thenReturn(annotationMetadata);
        when(annotationMetadata.hasAnnotation(SpringBootApplication.class.getCanonicalName())).thenReturn(true);
        when(metadataReader.getClassMetadata()).thenReturn(classMetadata);
        when(classMetadata.getClassName()).thenReturn(MockNacosPackageExcludeFilter.class.getCanonicalName());
        assertTrue(filter.match(metadataReader, metadataReaderFactory));
    }
    
    @Test
    void testMatchWithoutSpringBootApplicationAnnotation() throws IOException {
        when(metadataReader.getAnnotationMetadata()).thenReturn(annotationMetadata);
        when(metadataReader.getClassMetadata()).thenReturn(classMetadata);
        when(classMetadata.getClassName()).thenReturn(MockNacosPackageExcludeFilter.class.getCanonicalName(),
                MockNacosPackageExcludeFilter.class.getPackage().getName() + ".Test");
        assertTrue(filter.match(metadataReader, metadataReaderFactory));
        assertFalse(filter.match(metadataReader, metadataReaderFactory));
    }
    
    @Test
    void testMatchWithoutMatched() throws IOException {
        when(metadataReader.getAnnotationMetadata()).thenReturn(annotationMetadata);
        when(metadataReader.getClassMetadata()).thenReturn(classMetadata);
        when(classMetadata.getClassName()).thenReturn(NacosTypeExcludeFilterTest.class.getCanonicalName());
        assertFalse(filter.match(metadataReader, metadataReaderFactory));
    }
}