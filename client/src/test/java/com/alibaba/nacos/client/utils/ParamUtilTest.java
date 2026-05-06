/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParamUtilTest {
    
    private int defaultConnectTimeout;
    
    private int defaultReadTimeout;
    
    private double defaultPerTaskConfigSize;
    
    @BeforeEach
    void before() {
        defaultConnectTimeout = 1000;
        defaultReadTimeout = 3000;
        defaultPerTaskConfigSize = 3000.0;
    }
    
    @AfterEach
    void after() {
        ParamUtil.setConnectTimeout(defaultConnectTimeout);
        ParamUtil.setReadTimeout(defaultReadTimeout);
        ParamUtil.setPerTaskConfigSize(defaultPerTaskConfigSize);
        System.clearProperty("NACOS.CONNECT.TIMEOUT");
        System.clearProperty("NACOS_READ_TIMEOUT");
        System.clearProperty("PER_TASK_CONFIG_SIZE");
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
    }
    
    @Test
    void testSetConnectTimeout() {
        int defaultVal = ParamUtil.getConnectTimeout();
        assertEquals(defaultConnectTimeout, defaultVal);
        
        int expect = 50;
        ParamUtil.setConnectTimeout(expect);
        assertEquals(expect, ParamUtil.getConnectTimeout());
    }
    
    @Test
    void testSetReadTimeout() {
        int defaultVal = ParamUtil.getReadTimeout();
        assertEquals(defaultReadTimeout, defaultVal);
        
        int expect = 3000;
        ParamUtil.setReadTimeout(expect);
        assertEquals(expect, ParamUtil.getReadTimeout());
    }
    
    @Test
    void testGetPerTaskConfigSize() {
        double defaultVal = ParamUtil.getPerTaskConfigSize();
        assertEquals(defaultPerTaskConfigSize, defaultVal, 0.01);
        
        double expect = 50.0;
        ParamUtil.setPerTaskConfigSize(expect);
        assertEquals(expect, ParamUtil.getPerTaskConfigSize(), 0.01);
    }
    
    @Test
    void testInitConnectionTimeoutWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initConnectionTimeout");
            method.setAccessible(true);
            System.setProperty("NACOS.CONNECT.TIMEOUT", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testInitReadTimeoutWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initReadTimeout");
            method.setAccessible(true);
            System.setProperty("NACOS.READ.TIMEOUT", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testInitPerTaskConfigSizeWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initPerTaskConfigSize");
            method.setAccessible(true);
            System.setProperty("PER_TASK_CONFIG_SIZE", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testSimplyEnvNameIfOverLimit() {
        StringBuilder envNameOverLimitBuilder = new StringBuilder("test");
        for (int i = 0; i < 50; i++) {
            envNameOverLimitBuilder.append(i);
        }
        String envName = envNameOverLimitBuilder.toString();
        String actual = ParamUtil.simplyEnvNameIfOverLimit(envName);
        String expect = envName.substring(0, 50) + MD5Utils.md5Hex(envName, "UTF-8");
        assertEquals(expect, actual);
    }
    
    @Test
    void testSimplyEnvNameNotOverLimit() {
        String expect = "test";
        assertEquals(expect, ParamUtil.simplyEnvNameIfOverLimit(expect));
    }
}