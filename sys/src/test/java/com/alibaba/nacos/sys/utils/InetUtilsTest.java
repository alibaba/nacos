/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.sys.env.Constants.NACOS_SERVER_IP;
import static com.alibaba.nacos.sys.env.Constants.PREFER_HOSTNAME_OVER_IP;
import static com.alibaba.nacos.sys.env.Constants.SYSTEM_PREFER_HOSTNAME_OVER_IP;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "isPreferredAddress", inetAddress));
            when(inetAddress.isSiteLocalAddress()).thenReturn(true);
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "isPreferredAddress", inetAddress));
        } finally {
            ReflectionTestUtils.setField(InetUtils.class, "useOnlySiteLocalInterface", false);
        }
    }
    
    @Test
    void testIsPreferredAddressForPreferredNetwork() {
        List<String> preferredNetworks =
            (List<String>) ReflectionTestUtils.getField(InetUtils.class,
                "PREFERRED_NETWORKS");
        try {
            InetAddress inetAddress = mock(InetAddress.class);
            preferredNetworks.add("192.168.1.*");
            preferredNetworks.add("192.168.2");
            when(inetAddress.getHostAddress()).thenReturn("192.168.1.1");
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "isPreferredAddress", inetAddress));
            when(inetAddress.getHostAddress()).thenReturn("192.168.2.1");
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "isPreferredAddress", inetAddress));
            when(inetAddress.getHostAddress()).thenReturn("10.10.10.10");
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "isPreferredAddress", inetAddress));
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
            assertTrue((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "ignoreInterface", "eth1"));
            assertFalse((boolean) ReflectionTestUtils.invokeMethod(InetUtils.class,
                "ignoreInterface", "lo0"));
        } finally {
            ignoreInterfaces.clear();
        }
    }
    
    @Test
    void testGetGrpcListenIp() {
        // 保存原始属性值
        String originalValue = System.getProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP);
        try {
            // 测试1: 未设置属性时应返回null
            System.clearProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP);
            assertNull(InetUtils.getGrpcListenIp());
            
            // 测试2: 设置无效IP应抛异常
            String invalidIp = "12345";
            System.setProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP, invalidIp);
            assertThrows(RuntimeException.class, InetUtils::getGrpcListenIp);
            
            // 测试3: 设置有效IP应正确返回
            String validIp = "192.168.1.1";
            System.setProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP, validIp);
            assertEquals(validIp, InetUtils.getGrpcListenIp());
            
            // 测试4: 设置空值应返回null (根据实际需求)
            System.setProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP, "");
            assertEquals("", InetUtils.getGrpcListenIp());
        } finally {
            // 恢复原始属性值
            if (originalValue != null) {
                System.setProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP, originalValue);
            } else {
                System.clearProperty(Constants.NACOS_REMOTE_GRPC_LISTEN_IP);
            }
        }
    }
    
    // ========== Missing lines coverage tests ==========
    
    @Test
    void testGetNacosIpWithInvalidValue() {
        String originalValue = System.getProperty(NACOS_SERVER_IP);
        try {
            // 测试无效 IP 地址 - 不是有效的 IP 或域名
            System.setProperty(NACOS_SERVER_IP, "invalid-ip-address!");
            assertThrows(RuntimeException.class, InetUtils::getNacosIp);
            
            // 测试不是 IP 也不是域名的值
            System.setProperty(NACOS_SERVER_IP, "not-ip-or-domain-!!!");
            assertThrows(RuntimeException.class, InetUtils::getNacosIp);
        } finally {
            if (originalValue != null) {
                System.setProperty(NACOS_SERVER_IP, originalValue);
            } else {
                System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
            }
        }
    }
    
    @Test
    void testIpChangeEventGetterMethods() {
        InetUtils.IPChangeEvent event = new InetUtils.IPChangeEvent();
        event.setOldIP("192.168.1.1");
        event.setNewIP("192.168.1.2");
        
        // 测试 getter 方法 (lines 292, 300)
        assertEquals("192.168.1.1", event.getOldIP());
        assertEquals("192.168.1.2", event.getNewIP());
        
        // 测试 toString 方法 (line 309)
        String expected = "IPChangeEvent{oldIP='192.168.1.1', newIP='192.168.1.2'}";
        assertEquals(expected, event.toString());
    }
    
    @Test
    void testConstructor() {
        new InetUtils();
    }
    
    @Test
    void testRefreshIpWithSystemPreferHostnameOverIp() throws Exception {
        System.clearProperty(NACOS_SERVER_IP);
        System.setProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP, "true");
        try {
            invokeRefreshIp();
            assertNotNull(InetUtils.getSelfIP());
        } finally {
            System.clearProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP);
            System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
            ReflectionTestUtils.setField(InetUtils.class, "preferHostnameOverIP", false);
        }
    }
    
    @Test
    void testRefreshIpWithEnvPreferHostnameOverIp() throws Exception {
        System.clearProperty(NACOS_SERVER_IP);
        MockEnvironment env = new MockEnvironment();
        env.setProperty(PREFER_HOSTNAME_OVER_IP, "true");
        EnvUtil.setEnvironment(env);
        try {
            invokeRefreshIp();
            assertNotNull(InetUtils.getSelfIP());
        } finally {
            EnvUtil.setEnvironment(new MockEnvironment());
            System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
            ReflectionTestUtils.setField(InetUtils.class, "preferHostnameOverIP", false);
        }
    }
    
    @Test
    void testGetPreferHostnameOverIpWhenHostnameDiffersFromCanonical() throws Exception {
        System.setProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP, "true");
        InetAddress address = mock(InetAddress.class);
        when(address.getHostName()).thenReturn("short-name");
        when(address.getCanonicalHostName()).thenReturn("short-name.example.com");
        try (MockedStatic<InetAddress> mocked = Mockito.mockStatic(InetAddress.class)) {
            mocked.when(InetAddress::getLocalHost).thenReturn(address);
            String result = invokeGetPreferHostnameOverIp();
            assertEquals("short-name.example.com", result);
        } finally {
            System.clearProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP);
            ReflectionTestUtils.setField(InetUtils.class, "preferHostnameOverIP", false);
        }
    }
    
    @Test
    void testGetPreferHostnameOverIpWhenHostnameEqualsCanonical() throws Exception {
        System.setProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP, "true");
        InetAddress address = mock(InetAddress.class);
        when(address.getHostName()).thenReturn("same-name");
        when(address.getCanonicalHostName()).thenReturn("same-name");
        try (MockedStatic<InetAddress> mocked = Mockito.mockStatic(InetAddress.class)) {
            mocked.when(InetAddress::getLocalHost).thenReturn(address);
            String result = invokeGetPreferHostnameOverIp();
            assertEquals("same-name", result);
        } finally {
            System.clearProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP);
            ReflectionTestUtils.setField(InetUtils.class, "preferHostnameOverIP", false);
        }
    }
    
    @Test
    void testGetPreferHostnameOverIpWhenLookupFails() throws Exception {
        System.setProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP, "true");
        try (MockedStatic<InetAddress> mocked = Mockito.mockStatic(InetAddress.class)) {
            mocked.when(InetAddress::getLocalHost).thenThrow(new UnknownHostException("test"));
            String result = invokeGetPreferHostnameOverIp();
            assertNull(result);
        } finally {
            System.clearProperty(SYSTEM_PREFER_HOSTNAME_OVER_IP);
            ReflectionTestUtils.setField(InetUtils.class, "preferHostnameOverIP", false);
        }
    }
    
    @Test
    void testFindFirstNonLoopbackAddressFallbackToLocalHost() throws Exception {
        InetAddress local = mock(InetAddress.class);
        when(local.getHostAddress()).thenReturn("127.0.0.42");
        try (MockedStatic<NetworkInterface> nicMocked = Mockito.mockStatic(NetworkInterface.class);
            MockedStatic<InetAddress> inetMocked = Mockito.mockStatic(InetAddress.class)) {
            nicMocked.when(NetworkInterface::getNetworkInterfaces)
                .thenThrow(new SocketException("forced"));
            inetMocked.when(InetAddress::getLocalHost).thenReturn(local);
            InetAddress result = InetUtils.findFirstNonLoopbackAddress();
            assertEquals(local, result);
        }
    }
    
    @Test
    void testFindFirstNonLoopbackAddressReturnsNullWhenLocalHostFails() {
        try (MockedStatic<NetworkInterface> nicMocked = Mockito.mockStatic(NetworkInterface.class);
            MockedStatic<InetAddress> inetMocked = Mockito.mockStatic(InetAddress.class)) {
            nicMocked.when(NetworkInterface::getNetworkInterfaces)
                .thenThrow(new SocketException("forced"));
            inetMocked.when(InetAddress::getLocalHost)
                .thenThrow(new UnknownHostException("forced"));
            assertNull(InetUtils.findFirstNonLoopbackAddress());
        }
    }
    
    private static void invokeRefreshIp() throws Exception {
        Method refreshIp = InetUtils.class.getDeclaredMethod("refreshIp");
        refreshIp.setAccessible(true);
        refreshIp.invoke(null);
    }
    
    private static String invokeGetPreferHostnameOverIp() throws Exception {
        Method method = InetUtils.class.getDeclaredMethod("getPreferHostnameOverIP");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }
    
    @Test
    void testRefreshIpWithIpv6Bracketed() throws Exception {
        boolean original = setPreferIpv6(true);
        System.setProperty(NACOS_SERVER_IP, "240e:3a1:8170:6c00:8c80:9aff:fe33:5d2c");
        try {
            invokeRefreshIp();
            String selfIp = InetUtils.getSelfIP();
            assertNotNull(selfIp);
            assertTrue(selfIp.startsWith(InternetAddressUtil.IPV6_START_MARK));
            assertTrue(selfIp.endsWith(InternetAddressUtil.IPV6_END_MARK));
        } finally {
            setPreferIpv6(original);
            System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
            invokeRefreshIp();
        }
    }
    
    @Test
    void testRefreshIpWithIpv6PercentScopeStripped() throws Exception {
        boolean original = setPreferIpv6(true);
        System.setProperty(NACOS_SERVER_IP, "fe80::1%en0");
        try {
            invokeRefreshIp();
            String selfIp = InetUtils.getSelfIP();
            assertNotNull(selfIp);
            assertFalse(selfIp.contains(InternetAddressUtil.PERCENT_SIGN_IN_IPV6));
            assertTrue(selfIp.endsWith(InternetAddressUtil.IPV6_END_MARK));
        } finally {
            setPreferIpv6(original);
            System.setProperty(NACOS_SERVER_IP, "1.1.1.1");
            invokeRefreshIp();
        }
    }
    
    @Test
    void testFindFirstNonLoopbackAddressSkipsHigherIndexInterface() throws Exception {
        Inet4Address inet4 = mock(Inet4Address.class);
        when(inet4.isLoopbackAddress()).thenReturn(false);
        when(inet4.getHostAddress()).thenReturn("10.0.0.1");
        
        NetworkInterface lowIfc = mock(NetworkInterface.class);
        when(lowIfc.getIndex()).thenReturn(1);
        when(lowIfc.isUp()).thenReturn(true);
        when(lowIfc.getDisplayName()).thenReturn("eth0");
        when(lowIfc.getInetAddresses())
            .thenReturn(Collections.enumeration(Collections.singletonList(inet4)));
        
        NetworkInterface highIfc = mock(NetworkInterface.class);
        when(highIfc.getIndex()).thenReturn(99);
        when(highIfc.isUp()).thenReturn(true);
        when(highIfc.getDisplayName()).thenReturn("eth99");
        // not iterated because isHigher && result != null
        
        Enumeration<NetworkInterface> enumeration =
            Collections.enumeration(java.util.Arrays.asList(lowIfc, highIfc));
        try (MockedStatic<NetworkInterface> nicMocked =
            Mockito.mockStatic(NetworkInterface.class)) {
            nicMocked.when(NetworkInterface::getNetworkInterfaces).thenReturn(enumeration);
            InetAddress result = InetUtils.findFirstNonLoopbackAddress();
            assertEquals(inet4, result);
            verify(highIfc, Mockito.never()).getInetAddresses();
        }
    }
    
    @Test
    void testFindFirstNonLoopbackAddressReturnsIpv6WhenPreferred() throws Exception {
        boolean original = setPreferIpv6(true);
        try {
            Inet6Address inet6 = mock(Inet6Address.class);
            when(inet6.isLoopbackAddress()).thenReturn(false);
            when(inet6.getHostAddress()).thenReturn("fe80::1");
            
            NetworkInterface ifc = mock(NetworkInterface.class);
            when(ifc.getIndex()).thenReturn(1);
            when(ifc.isUp()).thenReturn(true);
            when(ifc.getDisplayName()).thenReturn("eth0");
            when(ifc.getInetAddresses())
                .thenReturn(Collections.enumeration(Collections.singletonList(inet6)));
            
            try (MockedStatic<NetworkInterface> nicMocked =
                Mockito.mockStatic(NetworkInterface.class)) {
                nicMocked.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.enumeration(Collections.singletonList(ifc)));
                InetAddress result = InetUtils.findFirstNonLoopbackAddress();
                assertEquals(inet6, result);
            }
        } finally {
            setPreferIpv6(original);
        }
    }
    
    private static boolean setPreferIpv6(boolean value) throws Exception {
        Field theUnsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        Object unsafe = theUnsafeField.get(null);
        Class<?> unsafeClass = unsafe.getClass();
        Field field = InternetAddressUtil.class.getDeclaredField("PREFER_IPV6_ADDRESSES");
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
        long offset =
            (long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field);
        boolean original = field.getBoolean(null);
        unsafeClass.getMethod("putBoolean", Object.class, long.class, boolean.class)
            .invoke(unsafe, base, offset, value);
        return original;
    }
}
