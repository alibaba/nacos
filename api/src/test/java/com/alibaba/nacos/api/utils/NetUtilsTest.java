/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;

import static org.junit.Assert.assertEquals;

public class NetUtilsTest {

    @After
    public void tearDown() throws Exception {
        Class<?> clazz = Class.forName("com.alibaba.nacos.api.utils.NetUtils");
        Field field = clazz.getDeclaredField("localIp");
        field.setAccessible(true);
        field.set(null, "");
        System.clearProperty("com.alibaba.nacos.client.local.ip");
        System.clearProperty("com.alibaba.nacos.client.local.preferHostname");
    }

    @Test
    public void testLocalIP() {
        System.setProperty("com.alibaba.nacos.client.naming.local.ip", "10.2.7.8");
        System.setProperty("com.alibaba.nacos.client.local.ip", "10.2.8.8");
        assertEquals("10.2.8.8", NetUtils.localIP());
    }

    @Test
    public void testPreferHostname() throws Exception {
        Class<?> clazz = Class.forName("com.alibaba.nacos.api.utils.NetUtils");
        Method method = clazz.getDeclaredMethod("findFirstNonLoopbackAddress");
        method.setAccessible(true);
        InetAddress inetAddress = (InetAddress) method.invoke(null);
        String hostname = inetAddress.getHostName();

        System.setProperty("com.alibaba.nacos.client.local.preferHostname", "true");
        assertEquals(hostname, NetUtils.localIP());
        assertEquals(hostname, NetUtils.localIP());
    }

}
