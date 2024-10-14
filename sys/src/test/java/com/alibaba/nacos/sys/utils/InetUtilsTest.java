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

package com.alibaba.nacos.sys.utils;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InetUtilsTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
        System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
        System.setProperty(Constants.AUTO_REFRESH_TIME, "100");
    }
    
    @Test
    void testRefreshIp() throws InterruptedException {
        assertNotEquals("1.1.1.2", InetUtils.getSelfIP());
        
        System.setProperty(NACOS_SERVER_IP, "1.1.1.2");
        TimeUnit.MILLISECONDS.sleep(300L);
        
        assertTrue(StringUtils.equalsIgnoreCase(InetUtils.getSelfIP(), "1.1.1.2"));
        
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(NACOS_SERVER_IP);
        System.clearProperty(Constants.AUTO_REFRESH_TIME);
    }
    
    @Test
    void getSelfIP() {
        assertNotNull(InetUtils.getSelfIP());
    }
    
    @Test
    void findFirstNonLoopbackAddress() {
        InetAddress address = InetUtils.findFirstNonLoopbackAddress();
        
        assertNotNull(address);
        assertFalse(address.isLoopbackAddress());
    }
    
    @Test
    void testisUp() throws SocketException {
        NetworkInterface nic = mock(NetworkInterface.class);
        when(nic.isUp()).thenReturn(true);
        assertTrue(InetUtils.isUp(nic));
        
        when(nic.isUp()).thenReturn(false);
        assertFalse(InetUtils.isUp(nic));
        
        when(nic.isUp()).thenThrow(new SocketException());
        assertFalse(InetUtils.isUp(nic));
    }
    
    @Test
    void testIsPreferredAddress() {
        try {
            ReflectionTestUtils.setField(InetUtils.class, "useOnlySiteLocalInterface", true);
            InetAddress inetAddress = mock(InetAddress.class);
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "isPreferredAddress", inetAddress));
            when(inetAddress.isSiteLocalAddress()).thenReturn(true);
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "isPreferredAddress", inetAddress));
        } finally {
            ReflectionTestUtils.setField(InetUtils.class, "useOnlySiteLocalInterface", false);
        }
    }
    
    @Test
    void testIsPreferredAddressForPreferredNetwork() {
        List<String> preferredNetworks = (List<String>) ReflectionTestUtils.getField(InetUtils.class,
                "PREFERRED_NETWORKS");
        try {
            InetAddress inetAddress = mock(InetAddress.class);
            preferredNetworks.add("192.168.1.*");
            preferredNetworks.add("192.168.2");
            when(inetAddress.getHostAddress()).thenReturn("192.168.1.1");
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "isPreferredAddress", inetAddress));
            when(inetAddress.getHostAddress()).thenReturn("192.168.2.1");
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "isPreferredAddress", inetAddress));
            when(inetAddress.getHostAddress()).thenReturn("10.10.10.10");
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "isPreferredAddress", inetAddress));
        } finally {
            preferredNetworks.clear();
        }
    }
    
    @Test
    void testIgnoreInterface() {
        List<String> ignoreInterfaces = (List<String>) ReflectionTestUtils.getField(InetUtils.class,
                "IGNORED_INTERFACES");
        try {
            ignoreInterfaces.add("eth.*");
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "ignoreInterface", "eth1"));
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class, "ignoreInterface", "lo0"));
        } finally {
            ignoreInterfaces.clear();
        }
    }
}
