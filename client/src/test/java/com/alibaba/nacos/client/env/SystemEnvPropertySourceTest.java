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

package com.alibaba.nacos.client.env;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Additional test cases for SystemEnvPropertySource.
 *
 * <p> Common cases see {@link NacosClientPropertiesTest}.</p>
 */
class SystemEnvPropertySourceTest {
    
    SystemEnvPropertySource systemEnvPropertySource;
    
    private Map<String, String> mockEnvMap;
    
    @BeforeEach
    void setUp() throws Exception {
        systemEnvPropertySource = new SystemEnvPropertySource();
        mockEnvMap = new HashMap<>();
        Field envField = SystemEnvPropertySource.class.getDeclaredField("env");
        envField.setAccessible(true);
        envField.set(systemEnvPropertySource, mockEnvMap);
        mockEnvMap.put("testcase1", "value1");
        mockEnvMap.put("test_case_2", "value2");
        mockEnvMap.put("TESTCASE3", "value3");
        mockEnvMap.put("TEST_CASE_4", "value4");
    }
    
    @Test
    void testGetEnvForLowerCaseKey() {
        assertEquals("value1", systemEnvPropertySource.getProperty("testcase1"));
    }
    
    @Test
    void testGetEnvForLowerCaseKeyWithDot() {
        assertEquals("value2", systemEnvPropertySource.getProperty("test.case.2"));
    }
    
    @Test
    void testGetEnvForLowerCaseKeyWithHyphen() {
        assertEquals("value2", systemEnvPropertySource.getProperty("test-case-2"));
    }
    
    @Test
    void testGetEnvForLowerCaseKeyWithHyphenAndDot() {
        assertEquals("value2", systemEnvPropertySource.getProperty("test.case-2"));
    }
    
    @Test
    void testGetEnvForUpperCaseKey() {
        assertEquals("value3", systemEnvPropertySource.getProperty("TESTCASE3"));
    }
    
    @Test
    void testGetEnvForUpperCaseKeyWithDot() {
        assertEquals("value4", systemEnvPropertySource.getProperty("TEST.CASE.4"));
    }
    
    @Test
    void testGetEnvForUpperCaseKeyWithHyphen() {
        assertEquals("value4", systemEnvPropertySource.getProperty("TEST-CASE-4"));
    }
    
    @Test
    void testGetEnvForUpperCaseKeyWithHyphenAndDot() {
        assertEquals("value4", systemEnvPropertySource.getProperty("TEST_CASE.4"));
    }
}