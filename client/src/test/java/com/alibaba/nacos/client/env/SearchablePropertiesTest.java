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

import com.alibaba.nacos.client.constant.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Additional test cases for SearchableProperties.
 *
 * <p> Common cases see {@link NacosClientPropertiesTest}.</p>
 */
class SearchablePropertiesTest {
    
    Method initMethod;
    
    @BeforeAll
    static void init() {
        System.setProperty(Constants.SysEnv.NACOS_ENV_FIRST, "jvm");
    }
    
    @AfterAll
    static void teardown() {
        System.clearProperty(Constants.SysEnv.NACOS_ENV_FIRST);
    }
    
    @BeforeEach
    void setUp() throws Exception {
        initMethod = SearchableProperties.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        init();
        initMethod.invoke(null);
    }
    
    @Test
    void testInitWithInvalidOrder() throws IllegalAccessException, InvocationTargetException {
        System.setProperty(Constants.SysEnv.NACOS_ENV_FIRST, "invalid");
        List<SourceType> order = (List<SourceType>) initMethod.invoke(null);
        assertOrder(order, SourceType.PROPERTIES, SourceType.JVM, SourceType.ENV);
    }
    
    @Test
    void testInitWithoutSpecifiedOrder() throws IllegalAccessException, InvocationTargetException {
        System.clearProperty(Constants.SysEnv.NACOS_ENV_FIRST);
        List<SourceType> order = (List<SourceType>) initMethod.invoke(null);
        assertOrder(order, SourceType.PROPERTIES, SourceType.JVM, SourceType.ENV);
    }
    
    private void assertOrder(List<SourceType> order, SourceType... sourceTypes) {
        assertEquals(sourceTypes.length, order.size());
        for (int i = 0; i < sourceTypes.length; i++) {
            assertEquals(sourceTypes[i], order.get(i));
        }
    }
    
    @Test
    void testGetPropertyFromEnv() {
        System.setProperty("testFromSource", "jvm");
        NacosClientProperties properties = SearchableProperties.INSTANCE.derive();
        properties.setProperty("testFromSource", "properties");
        assertNull(properties.getPropertyFrom(SourceType.ENV, "testFromSource"));
    }
    
    @Test
    void testGetPropertyFromUnknown() {
        System.setProperty("testFromSource", "jvm");
        NacosClientProperties properties = SearchableProperties.INSTANCE.derive();
        properties.setProperty("testFromSource", "properties");
        assertEquals(properties.getProperty("testFromSource"),
                properties.getPropertyFrom(SourceType.UNKNOWN, "testFromSource"));
    }
}