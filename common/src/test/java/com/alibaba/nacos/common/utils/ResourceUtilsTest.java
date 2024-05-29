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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceUtilsTest {
    
    @Test
    void testGetResourceUrlForClasspath() throws IOException {
        URL url = ResourceUtils.getResourceUrl("classpath:test-tls-cert.pem");
        assertNotNull(url);
    }
    
    @Test
    void testGetResourceUrlForClasspathNotExists() throws IOException {
        assertThrows(FileNotFoundException.class, () -> {
            ResourceUtils.getResourceUrl("classpath:non-exist.pem");
        });
    }
    
    @Test
    void testGetResourceUrlForFile() throws IOException {
        File file = File.createTempFile("test", ".txt");
        try {
            URL url = ResourceUtils.getResourceUrl("file://" + file.getPath());
            assertNotNull(url);
        } finally {
            file.deleteOnExit();
        }
    }
    
    @Test
    void testGetResourceUrlForFileWithoutProtocol() throws IOException {
        File file = File.createTempFile("test", ".txt");
        try {
            URL url = ResourceUtils.getResourceUrl(file.getPath());
            assertNotNull(url);
        } finally {
            file.deleteOnExit();
        }
    }
    
    @Test
    void testGetResourceUrlFromLoader() throws IOException {
        URL url = ResourceUtils.getResourceUrl(this.getClass().getClassLoader(), "test-tls-cert.pem");
        assertNotNull(url);
    }
    
    @Test
    void testGetResourceUrlFromSystemLoader() throws IOException {
        URL url = ResourceUtils.getResourceUrl(null, "test-tls-cert.pem");
        assertNotNull(url);
    }
    
    @Test
    void testGetResourceUrlFromLoaderWithoutExist() throws IOException {
        assertThrows(IOException.class, () -> {
            URL url = ResourceUtils.getResourceUrl(null, "non-exist");
            assertNotNull(url);
        });
    }
    
    @Test
    void testGetResourceAsStreamForClasspath() throws IOException {
        try (InputStream inputStream = ResourceUtils.getResourceAsStream("test-tls-cert.pem")) {
            assertNotNull(inputStream);
        }
    }
    
    @Test
    void testGetResourceAsStreamForClasspathFromSystem() throws IOException {
        try (InputStream inputStream = ResourceUtils.getResourceAsStream(null, "test-tls-cert.pem")) {
            assertNotNull(inputStream);
        }
    }
    
    @Test
    void testGetResourceAsStreamForClasspathWithoutExist() throws IOException {
        assertThrows(IOException.class, () -> {
            URL url = ResourceUtils.getResourceUrl("non-exist");
            ResourceUtils.getResourceAsStream(null, url.toString());
        });
    }
    
    @Test
    void testGetResourceAsPropertiesForClasspath() throws IOException {
        Properties properties = ResourceUtils.getResourceAsProperties("resource_utils_test.properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("a"));
    }
    
    @Test
    void testGetResourceAsReader() throws IOException {
        try (Reader reader = ResourceUtils.getResourceAsReader("resource_utils_test.properties", "UTF-8")) {
            assertNotNull(reader);
        }
    }
    
    @Test
    void testGetResourceAsReaderWithLoader() throws IOException {
        try (Reader reader = ResourceUtils.getResourceAsReader(ResourceUtilsTest.class.getClassLoader(),
                "resource_utils_test.properties", "UTF-8")) {
            assertNotNull(reader);
        }
    }
    
    @Test
    void testGetResourceAsFile() throws IOException {
        File file = ResourceUtils.getResourceAsFile("classpath:resource_utils_test.properties");
        assertNotNull(file);
    }
    
    @Test
    void testGetResourceAsFileByUrl() throws IOException {
        File file = ResourceUtils.getResourceAsFile(ResourceUtils.getResourceUrl("classpath:resource_utils_test.properties"));
        assertNotNull(file);
    }
    
    @Test
    void testGetResourceAsFileByLoader() throws IOException {
        File file = ResourceUtils.getResourceAsFile(ResourceUtils.class.getClassLoader(), "resource_utils_test.properties");
        assertNotNull(file);
    }
}