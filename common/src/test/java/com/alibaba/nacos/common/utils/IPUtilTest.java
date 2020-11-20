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

/**
 * test IpUtil.
 * @ClassName: IpUtilTest
 * @date 2020/9/3 10:31
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class IPUtilTest {
    
    @Test
    public void testIsIPv4() {
        Assert.assertTrue(IPUtil.isIPv4("127.0.0.1"));
        Assert.assertFalse(IPUtil.isIPv4("[::1]"));
        Assert.assertFalse(IPUtil.isIPv4("asdfasf"));
        Assert.assertFalse(IPUtil.isIPv4("ffgertert"));
    }
    
    @Test
    public void testIsIPv6() {
        Assert.assertTrue(IPUtil.isIPv6("[::1]"));
        Assert.assertFalse(IPUtil.isIPv6("127.0.0.1"));
        Assert.assertFalse(IPUtil.isIPv6("er34234"));
    }
    
    @Test
    public void testIsIP() {
        Assert.assertTrue(IPUtil.isIP("[::1]"));
        Assert.assertTrue(IPUtil.isIP("127.0.0.1"));
        Assert.assertFalse(IPUtil.isIP("er34234"));
    }
    
    @Test
    public void testGetIPFromString() {
        Assert.assertEquals("[::1]", IPUtil.getIPFromString("http://[::1]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("[::1]", IPUtil.getIPFromString(
                "jdbc:mysql://[::1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("127.0.0.1",
                IPUtil.getIPFromString("http://127.0.0.1:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("127.0.0.1", IPUtil.getIPFromString(
                "jdbc:mysql://127.0.0.1:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
    
        Assert.assertEquals("",
                IPUtil.getIPFromString("http://[dddd]:666/xzdsfasdf/awerwef" + "?eewer=2&xxx=3"));
        Assert.assertEquals("", IPUtil.getIPFromString(
                "jdbc:mysql://[127.0.0.1]:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
        Assert.assertEquals("", IPUtil.getIPFromString(
                "jdbc:mysql://666.288.333.444:3306/nacos_config_test?characterEncoding=utf8&connectTimeout=1000"
                        + "&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC"));
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
        checkSplitIPPortStr("[fe80::3ce6:7132:808e:707a%19]:88", false, "[fe80::3ce6:7132:808e:707a%19]", "88");
    
        checkSplitIPPortStr("::1:88", true);
        checkSplitIPPortStr("[::1:88", true);
        checkSplitIPPortStr("[127.0.0.1]:88", true);
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
            String[] array = IPUtil.splitIPPortStr(addr);
            Assert.assertTrue(array.length == equalsStrs.length);
            if (array.length > 1) {
                Assert.assertTrue(array[0].equals(equalsStrs[0]));
                Assert.assertTrue(array[1].equals(equalsStrs[1]));
            } else {
                Assert.assertTrue(array[0].equals(equalsStrs[0]));
            }
        } catch (Exception ex) {
            if (!isEx) {
                // No exception is expected here, but an exception has occurred
                Assert.assertTrue("Unexpected exception", false);
            }
        }
    }
    
}
