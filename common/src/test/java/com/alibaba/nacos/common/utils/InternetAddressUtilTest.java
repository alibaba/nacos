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
    public static void checkSplitIPPortStr(String addr, boolean isEx, String... equalsStrs) {
        try {
            String[] array = InternetAddressUtil.splitIPPortStr(addr);
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
    void testIsIPv4() {
        assertTrue(InternetAddressUtil.isIPv4("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIPv4("[::1]"));
        assertFalse(InternetAddressUtil.isIPv4("asdfasf"));
        assertFalse(InternetAddressUtil.isIPv4("ffgertert"));
        assertFalse(InternetAddressUtil.isIPv4("127.100.19"));
    }
    
    @Test
    void testIsIPv6() {
        assertTrue(InternetAddressUtil.isIPv6("[::1]"));
        assertFalse(InternetAddressUtil.isIPv6("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIPv6("er34234"));
    }
    
    @Test
    void testIsIP() {
        assertTrue(InternetAddressUtil.isIP("[::1]"));
        assertTrue(InternetAddressUtil.isIP("127.0.0.1"));
        assertFalse(InternetAddressUtil.isIP("er34234"));
        assertFalse(InternetAddressUtil.isIP("127.100.19"));
    }
    
    @Test
    void testGetIPFromString() {
        assertEquals("[::1]", InternetAddressUtil.getIPFromString("http://[::1]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("[::1]", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://[::1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("127.0.0.1",
                InternetAddressUtil.getIPFromString("http://127.0.0.1:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("127.0.0.1", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://127.0.0.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString("http://[::1:666"));
        
        assertEquals("", InternetAddressUtil.getIPFromString("http://[dddd]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://[127.0.0.1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://666.288.333.444:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://292.168.1.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.168.1.288:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.168.288.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.288.28.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        assertEquals("", InternetAddressUtil.getIPFromString(""));
        assertEquals("", InternetAddressUtil.getIPFromString(null));
    }
    
    @Test
    void testSplitIpPort() {
        checkSplitIPPortStr("[::1]:88", false, "[::1]", "88");
        checkSplitIPPortStr("[::1]", false, "[::1]");
        checkSplitIPPortStr("127.0.0.1:88", false, "127.0.0.1", "88");
        checkSplitIPPortStr("127.0.0.1", false, "127.0.0.1");
        checkSplitIPPortStr("[2001:db8:0:0:1:0:0:1]:88", false, "[2001:db8:0:0:1:0:0:1]", "88");
        checkSplitIPPortStr("[2001:0db8:0:0:1:0:0:1]:88", false, "[2001:0db8:0:0:1:0:0:1]", "88");
        checkSplitIPPortStr("[2001:db8::1:0:0:1]:88", false, "[2001:db8::1:0:0:1]", "88");
        checkSplitIPPortStr("[2001:db8::0:1:0:0:1]:88", false, "[2001:db8::0:1:0:0:1]", "88");
        checkSplitIPPortStr("[2001:0db8::1:0:0:1]:88", false, "[2001:0db8::1:0:0:1]", "88");
        checkSplitIPPortStr("[2001:db8:0:0:1::1]:88", false, "[2001:db8:0:0:1::1]", "88");
        checkSplitIPPortStr("[2001:db8:0000:0:1::1]:88", false, "[2001:db8:0000:0:1::1]", "88");
        checkSplitIPPortStr("[2001:DB8:0:0:1::1]:88", false, "[2001:DB8:0:0:1::1]", "88");
        checkSplitIPPortStr("localhost:8848", false, "localhost", "8848");
        checkSplitIPPortStr("[dead::beef]:88", false, "[dead::beef]", "88");
        
        // illegal ip will get abnormal results
        checkSplitIPPortStr("::1:88", false, "", "", "1", "88");
        checkSplitIPPortStr("[::1:88", false, "[", "", "1", "88");
        checkSplitIPPortStr("[127.0.0.1]:88", false, "[127.0.0.1]", "88");
        checkSplitIPPortStr("[dead:beef]:88", false, "[dead:beef]", "88");
        checkSplitIPPortStr("[fe80::3ce6:7132:808e:707a%19]:88", false, "[fe80::3ce6:7132:808e:707a%19]", "88");
        checkSplitIPPortStr("[fe80::3]e6]:88", false, "[fe80::3]", "6]:88");
        checkSplitIPPortStr("", true);
    }
    
    @Test
    void testCheckIPs() {
        assertEquals("ok", InternetAddressUtil.checkIPs("127.0.0.1"));
        assertEquals("ok", InternetAddressUtil.checkIPs());
        assertEquals("ok", InternetAddressUtil.checkIPs());
        assertEquals("ok", InternetAddressUtil.checkIPs(null));
        
        assertEquals("illegal ip: 127.100.19", InternetAddressUtil.checkIPs("127.100.19", "127.0.0.1"));
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
        assertTrue(InternetAddressUtil.checkOK("ok"));
        assertFalse(InternetAddressUtil.checkOK("ojbk"));
    }
    
    @Test
    void testContainsPort() {
        assertTrue(InternetAddressUtil.containsPort("127.0.0.1:80"));
        assertFalse(InternetAddressUtil.containsPort("127.0.0.1:80:80"));
    }
    
    @Test
    void testLocalHostIP() throws NoSuchFieldException, IllegalAccessException {
        Field field = InternetAddressUtil.class.getField("PREFER_IPV6_ADDRESSES");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        
        field.set(null, false);
        assertEquals("127.0.0.1", InternetAddressUtil.localHostIP());
        
        field.set(null, true);
        assertEquals("[::1]", InternetAddressUtil.localHostIP());
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
