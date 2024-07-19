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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InetAddressValidator Test.
 *
 * @ClassName: InetAddressValidatorTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 13:07
 */
class InetAddressValidatorTest {
    
    @Test
    void isIpv6Address() {
        assertTrue(InetAddressValidator.isIpv6Address("2000:0000:0000:0000:0001:2345:6789:abcd"));
        assertTrue(InetAddressValidator.isIpv6Address("2001:DB8:0:0:8:800:200C:417A"));
        assertTrue(InetAddressValidator.isIpv6Address("2001:DB8::8:800:200C:417A"));
        assertFalse(InetAddressValidator.isIpv6Address("2001:DB8::8:800:200C141aA"));
    }
    
    @Test
    void isIpv6MixedAddress() {
        assertTrue(InetAddressValidator.isIpv6MixedAddress("1:0:0:0:0:0:172.12.55.18"));
        assertTrue(InetAddressValidator.isIpv6MixedAddress("::172.12.55.18"));
        assertFalse(InetAddressValidator.isIpv6MixedAddress("2001:DB8::8:800:200C141aA"));
    }
    
    @Test
    void isIpv6Ipv4MappedAddress() {
        assertFalse(InetAddressValidator.isIpv6Ipv4MappedAddress(":ffff:1.1.1.1"));
        assertTrue(InetAddressValidator.isIpv6Ipv4MappedAddress("::FFFF:192.168.1.2"));
    }
    
    @Test
    void isIpv4Address() {
        assertTrue(InetAddressValidator.isIpv4Address("192.168.1.2"));
    }
    
    @Test
    void isLinkLocalIpv6WithZoneIndex() {
        assertTrue(InetAddressValidator.isLinkLocalIpv6WithZoneIndex("fe80::1%lo0"));
        assertFalse(InetAddressValidator.isLinkLocalIpv6WithZoneIndex("2000:0000:0000:0000:0001:2345:6789:abcd"));
    }
}
