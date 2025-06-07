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

package com.alibaba.nacos.maintainer.client.utils;

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
    
    @BeforeEach
    void before() {
        defaultConnectTimeout = ParamUtil.getConnectTimeout();
        defaultReadTimeout = ParamUtil.getReadTimeout();
    }
    
    @AfterEach
    void after() {
        ParamUtil.setConnectTimeout(defaultConnectTimeout);
        ParamUtil.setReadTimeout(defaultReadTimeout);
        System.clearProperty("MAINTAINER.CLIENT.CONNECT.TIMEOUT");
        System.clearProperty("MAINTAINER.CLIENT.READ.TIMEOUT");
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
    void testInitConnectionTimeoutWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initConnectionTimeout");
            method.setAccessible(true);
            System.setProperty("MAINTAINER.CLIENT.CONNECT.TIMEOUT", "test");
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
            System.setProperty("MAINTAINER.CLIENT.READ.TIMEOUT", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testInitMaxRetryTimesWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initMaxRetryTimes");
            method.setAccessible(true);
            System.setProperty("MAINTAINER.CLIENT.MAX.RETRY.TIMES", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testInitRefreshIntervalMillsWithException() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> {
            Method method = ParamUtil.class.getDeclaredMethod("initRefreshIntervalMills");
            method.setAccessible(true);
            System.setProperty("MAINTAINER.CLIENT.REFRESH.INTERVAL.MILLS", "test");
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
