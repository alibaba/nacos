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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * test IpUtil.
 *
 * @ClassName: IpUtilTest
 * @date 2020/9/3 10:31
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class InternetAddressUtilTest {
    
    /**
     * checkSplitIpPortStr. 2020/9/4 14:12
     *
     * @param addr       addr
     * @param isEx       isEx
     * @param equalsStrs equalsStrs
     */
    public static void checkSplitIpPortStr(String addr, boolean isEx, String... equalsStrs) {
        try {
            String[] array = InternetAddressUtil.splitIpPortStr(addr);
            assertEquals(array.length, equalsStrs.length);
            for (int i = 0; i < array.length; i++) {
                assertEquals(array[i], equalsStrs[i]);
            }
        } catch (Exception ex) {
            if (!isEx) {
                // No exception is expected here, but an exception has occurred
                fail("Unexpected exception");
            }
        }
    }
    
    @Test
    void testIsIpv4() {
        assertTrue(InternetAddressUtil.isIpv4("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIpv4("[::1]"));
        assertFalse(InternetAddressUtil.isIpv4("asdfasf"));
        assertFalse(InternetAddressUtil.isIpv4("ffgertert"));
        assertFalse(InternetAddressUtil.isIpv4("127.100.19"));
    }
    
    @Test
    void testIsIpv6() {
        assertTrue(InternetAddressUtil.isIpv6("[::1]"));
        assertFalse(InternetAddressUtil.isIpv6("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIpv6("er34234"));
    }
    
    @Test
    void testIsIp() {
        assertTrue(InternetAddressUtil.isIp("[::1]"));
        assertTrue(InternetAddressUtil.isIp("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIp("er34234"));
        assertFalse(InternetAddressUtil.isIp("127.100.19"));
    }
    
    @Test
    void testGetIpFromString() {
        assertEquals("[::1]", InternetAddressUtil.getIpFromString("http://[::1]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("[::1]", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://[::1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("127.0.0.1",
                InternetAddressUtil.getIpFromString("http://127.0.0.1:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("127.0.0.1", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://127.0.0.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString("http://[::1:666"));
        
        assertEquals("", InternetAddressUtil.getIpFromString("http://[dddd]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://[127.0.0.1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://666.288.333.444:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://292.168.1.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://29.168.1.288:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://29.168.288.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(
                "jdbc:mysql://29.288.28.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIpFromString(""));
        assertEquals("", InternetAddressUtil.getIpFromString(null));
    }
    
    @Test
    void testSplitIpPort() {
        checkSplitIpPortStr("[::1]:88", false, "[::1]", "88");
        checkSplitIpPortStr("[::1]", false, "[::1]");
        checkSplitIpPortStr("127.0.0.1:88", false, "127.0.0.1", "88");
        checkSplitIpPortStr("127.0.0.1", false, "127.0.0.1");
        checkSplitIpPortStr("[2001:db8:0:0:1:0:0:1]:88", false, "[2001:db8:0:0:1:0:0:1]", "88");
        checkSplitIpPortStr("[2001:0db8:0:0:1:0:0:1]:88", false, "[2001:0db8:0:0:1:0:0:1]", "88");
        checkSplitIpPortStr("[2001:db8::1:0:0:1]:88", false, "[2001:db8::1:0:0:1]", "88");
        checkSplitIpPortStr("[2001:db8::0:1:0:0:1]:88", false, "[2001:db8::0:1:0:0:1]", "88");
        checkSplitIpPortStr("[2001:0db8::1:0:0:1]:88", false, "[2001:0db8::1:0:0:1]", "88");
        checkSplitIpPortStr("[2001:db8:0:0:1::1]:88", false, "[2001:db8:0:0:1::1]", "88");
        checkSplitIpPortStr("[2001:db8:0000:0:1::1]:88", false, "[2001:db8:0000:0:1::1]", "88");
        checkSplitIpPortStr("[2001:DB8:0:0:1::1]:88", false, "[2001:DB8:0:0:1::1]", "88");
        checkSplitIpPortStr("localhost:8848", false, "localhost", "8848");
        checkSplitIpPortStr("[dead::beef]:88", false, "[dead::beef]", "88");
        
        // illegal ip will get abnormal results
        checkSplitIpPortStr("::1:88", false, "", "", "1", "88");
        checkSplitIpPortStr("[::1:88", false, "[", "", "1", "88");
        checkSplitIpPortStr("[127.0.0.1]:88", false, "[127.0.0.1]", "88");
        checkSplitIpPortStr("[dead:beef]:88", false, "[dead:beef]", "88");
        checkSplitIpPortStr("[fe80::3ce6:7132:808e:707a%19]:88", false, "[fe80::3ce6:7132:808e:707a%19]", "88");
        checkSplitIpPortStr("[fe80::3]e6]:88", false, "[fe80::3]", "6]:88");
        checkSplitIpPortStr("", true);
    }
    
    @Test
    void testCheckIPs() {
        assertEquals("ok", InternetAddressUtil.checkIps("127.0.0.1"));
        assertEquals("ok", InternetAddressUtil.checkIps());
        assertEquals("ok", InternetAddressUtil.checkIps());
        assertEquals("ok", InternetAddressUtil.checkIps(null));
        
        assertEquals("illegal ip: 127.100.19", InternetAddressUtil.checkIps("127.100.19", "127.0.0.1"));
    }
    
    @Test
    void testIsDomain() {
        assertTrue(InternetAddressUtil.isDomain("localhost"));
        assertTrue(InternetAddressUtil.isDomain("github.com"));
        assertTrue(InternetAddressUtil.isDomain("prefix.infix.suffix"));
        assertTrue(InternetAddressUtil.isDomain("p-hub.com"));
        
        assertFalse(InternetAddressUtil.isDomain(""));
        assertFalse(InternetAddressUtil.isDomain(null));
    }
    
    @Test
    void testRemoveBrackets() {
        assertEquals("2001:DB8:0:0:1::1", InternetAddressUtil.removeBrackets("[2001:DB8:0:0:1::1]"));
        assertEquals("2077", InternetAddressUtil.removeBrackets("[2077[]]]"));
        assertEquals("", InternetAddressUtil.removeBrackets(""));
        assertEquals("", InternetAddressUtil.removeBrackets(null));
    }
    
    @Test
    void testCheckOk() {
        assertTrue(InternetAddressUtil.checkOk("ok"));
        assertFalse(InternetAddressUtil.checkOk("ojbk"));
    }
    
    @Test
    void testContainsPort() {
        assertTrue(InternetAddressUtil.containsPort("127.0.0.1:80"));
        assertFalse(InternetAddressUtil.containsPort("127.0.0.1:80:80"));
    }
    
    @Test
    void testLocalHostIp()
            throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Field field = InternetAddressUtil.class.getField("PREFER_IPV6_ADDRESSES");
        field.setAccessible(true);
        Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
        getDeclaredFields0.setAccessible(true);
        Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
        Field modifiersField = null;
        for (Field each : fields) {
            if ("modifiers".equals(each.getName())) {
                modifiersField = each;
            }
        }
        if (modifiersField != null) {
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        }
        
        field.set(null, false);
        assertEquals("127.0.0.1", InternetAddressUtil.localHostIp());
        
        field.set(null, true);
        assertEquals("[::1]", InternetAddressUtil.localHostIp());
    }
    
    @Test
    void testIpToInt() {
        assertEquals(2130706433, InternetAddressUtil.ipToInt("127.0.0.1"));
        assertEquals(-1062731775, InternetAddressUtil.ipToInt("192.168.0.1"));
    }
    
    @Test
    void testIllegalIpToInt() {
        assertThrows(IllegalArgumentException.class, () -> {
            InternetAddressUtil.ipToInt("127.0.0.256");
        });
    }
}
