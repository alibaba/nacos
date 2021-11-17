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

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * test IpUtil.
 * @ClassName: IpUtilTest
 * @date 2020/9/3 10:31
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class InternetAddressUtilTest {
    
    @Test
    public void testIsIPv4() {
        Assert.assertTrue(InternetAddressUtil.isIPv4("127.0.0.1"));
        Assert.assertFalse(InternetAddressUtil.isIPv4("[::1]"));
        Assert.assertFalse(InternetAddressUtil.isIPv4("asdfasf"));
        Assert.assertFalse(InternetAddressUtil.isIPv4("ffgertert"));
        Assert.assertFalse(InternetAddressUtil.isIPv4("127.100.19"));
    }
    
    @Test
    public void testIsIPv6() {
        Assert.assertTrue(InternetAddressUtil.isIPv6("[::1]"));
        Assert.assertFalse(InternetAddressUtil.isIPv6("127.0.0.1"));
        Assert.assertFalse(InternetAddressUtil.isIPv6("er34234"));
    }
    
    @Test
    public void testIsIP() {
        Assert.assertTrue(InternetAddressUtil.isIP("[::1]"));
        Assert.assertTrue(InternetAddressUtil.isIP("127.0.0.1"));
        Assert.assertFalse(InternetAddressUtil.isIP("er34234"));
        Assert.assertFalse(InternetAddressUtil.isIP("127.100.19"));
    }
    
    @Test
    public void testGetIPFromString() {
        Assert.assertEquals("[::1]", InternetAddressUtil.getIPFromString("http://[::1]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("[::1]", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://[::1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("127.0.0.1",
                InternetAddressUtil.getIPFromString("http://127.0.0.1:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("127.0.0.1", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://127.0.0.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString("http://[::1:666"));
    
        Assert.assertEquals("",
                InternetAddressUtil.getIPFromString("http://[dddd]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://[127.0.0.1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://666.288.333.444:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://292.168.1.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.168.1.288:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.168.288.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(
                "jdbc:mysql://29.288.28.28:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(""));
        Assert.assertEquals("", InternetAddressUtil.getIPFromString(null));
    }
    
    @Test
    public void testSplitIpPort() {
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
    public void testCheckIPs() {
        Assert.assertEquals("ok", InternetAddressUtil.checkIPs("127.0.0.1"));
        Assert.assertEquals("ok", InternetAddressUtil.checkIPs());
        Assert.assertEquals("ok", InternetAddressUtil.checkIPs());
        Assert.assertEquals("ok", InternetAddressUtil.checkIPs(null));
        
        Assert.assertEquals("illegal ip: 127.100.19", InternetAddressUtil.checkIPs("127.100.19", "127.0.0.1"));
    }
    
    @Test
    public void testIsDomain() {
        Assert.assertTrue(InternetAddressUtil.isDomain("localhost"));
        Assert.assertTrue(InternetAddressUtil.isDomain("github.com"));
        Assert.assertTrue(InternetAddressUtil.isDomain("prefix.infix.suffix"));
        Assert.assertTrue(InternetAddressUtil.isDomain("p-hub.com"));
        
        Assert.assertFalse(InternetAddressUtil.isDomain(""));
        Assert.assertFalse(InternetAddressUtil.isDomain(null));
    }
    
    @Test
    public void testRemoveBrackets() {
        Assert.assertEquals(InternetAddressUtil.removeBrackets("[2001:DB8:0:0:1::1]"), "2001:DB8:0:0:1::1");
        Assert.assertEquals(InternetAddressUtil.removeBrackets("[2077[]]]"), "2077");
        Assert.assertEquals(InternetAddressUtil.removeBrackets(""), "");
        Assert.assertEquals(InternetAddressUtil.removeBrackets(null), "");
    }
    
    @Test
    public void testCheckOk() {
        Assert.assertTrue(InternetAddressUtil.checkOK("ok"));
        Assert.assertFalse(InternetAddressUtil.checkOK("ojbk"));
    }
    
    @Test
    public void testContainsPort() {
        Assert.assertTrue(InternetAddressUtil.containsPort("127.0.0.1:80"));
        Assert.assertFalse(InternetAddressUtil.containsPort("127.0.0.1:80:80"));
    }
    
    @Test
    public void testLocalHostIP() throws NoSuchFieldException, IllegalAccessException {
        Field field = InternetAddressUtil.class.getField("PREFER_IPV6_ADDRESSES");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        
        field.set(null, false);
        Assert.assertEquals("127.0.0.1", InternetAddressUtil.localHostIP());
        
        field.set(null, true);
        Assert.assertEquals("[::1]", InternetAddressUtil.localHostIP());
    }
    
    /**
     * checkSplitIpPortStr.
     * 2020/9/4 14:12
     * @param addr addr
     * @param isEx isEx
     * @param equalsStrs equalsStrs
     */
    public static void checkSplitIPPortStr(String addr, boolean isEx, String... equalsStrs) {
        try {
            String[] array = InternetAddressUtil.splitIPPortStr(addr);
            Assert.assertEquals(array.length, equalsStrs.length);
            for (int i = 0; i < array.length; i++) {
                Assert.assertEquals(array[i], equalsStrs[i]);
            }
        } catch (Exception ex) {
            if (!isEx) {
                // No exception is expected here, but an exception has occurred
                Assert.fail("Unexpected exception");
            }
        }
    }
    
}
