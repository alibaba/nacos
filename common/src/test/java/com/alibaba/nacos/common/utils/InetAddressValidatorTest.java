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
 * InetAddressValidator Test.
 * @ClassName: InetAddressValidatorTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 13:07
 */
public class InetAddressValidatorTest {
    
    @Test
    public void isIPv6Address() {
        Assert.assertTrue(InetAddressValidator.isIPv6Address("2000:0000:0000:0000:0001:2345:6789:abcd"));
        Assert.assertTrue(InetAddressValidator.isIPv6Address("2001:DB8:0:0:8:800:200C:417A"));
        Assert.assertTrue(InetAddressValidator.isIPv6Address("2001:DB8::8:800:200C:417A"));
        Assert.assertFalse(InetAddressValidator.isIPv6Address("2001:DB8::8:800:200C141aA"));
    }
    
    @Test
    public void isIPv6MixedAddress() {
        Assert.assertTrue(InetAddressValidator.isIPv6MixedAddress("1:0:0:0:0:0:172.12.55.18"));
        Assert.assertTrue(InetAddressValidator.isIPv6MixedAddress("::172.12.55.18"));
        Assert.assertFalse(InetAddressValidator.isIPv6MixedAddress("2001:DB8::8:800:200C141aA"));
    }
    
    @Test
    public void isIPv6IPv4MappedAddress() {
        Assert.assertFalse(InetAddressValidator.isIPv6IPv4MappedAddress(":ffff:1.1.1.1"));
        Assert.assertTrue(InetAddressValidator.isIPv6IPv4MappedAddress("::FFFF:192.168.1.2"));
    }
    
    @Test
    public void isIPv4Address() {
        Assert.assertTrue(InetAddressValidator.isIPv4Address("192.168.1.2"));
    }
    
    @Test
    public void isLinkLocalIPv6WithZoneIndex() {
        Assert.assertTrue(InetAddressValidator.isLinkLocalIPv6WithZoneIndex("fe80::1%lo0"));
        Assert.assertFalse(InetAddressValidator.isLinkLocalIPv6WithZoneIndex("2000:0000:0000:0000:0001:2345:6789:abcd"));
    }
}
