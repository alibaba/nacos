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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRequestInfoTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigRequestInfo info = new ConfigRequestInfo();
        assertNull(info.getSrcIp());
        assertNull(info.getSrcType());
        assertNull(info.getRequestIpApp());
        assertNull(info.getBetaIps());
        assertNull(info.getCasMd5());
        assertTrue(info.getUpdateForExist());
    }
    
    @Test
    void testParameterizedConstructor() {
        ConfigRequestInfo info = new ConfigRequestInfo("1.1.1.1", "http", "app",
            "2.2.2.2", "md5");
        assertEquals("1.1.1.1", info.getSrcIp());
        assertEquals("http", info.getSrcType());
        assertEquals("app", info.getRequestIpApp());
        assertEquals("2.2.2.2", info.getBetaIps());
        assertEquals("md5", info.getCasMd5());
    }
    
    @Test
    void testSettersAndGetters() {
        ConfigRequestInfo info = new ConfigRequestInfo();
        info.setSrcIp("ip");
        info.setSrcType("type");
        info.setRequestIpApp("app");
        info.setBetaIps("beta");
        info.setCasMd5("cas");
        info.setUpdateForExist(false);
        
        assertEquals("ip", info.getSrcIp());
        assertEquals("type", info.getSrcType());
        assertEquals("app", info.getRequestIpApp());
        assertEquals("beta", info.getBetaIps());
        assertEquals("cas", info.getCasMd5());
        assertFalse(info.getUpdateForExist());
    }
    
    @Test
    void testEquals() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        ConfigRequestInfo b = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsSameInstance() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        assertEquals(a, a);
    }
    
    @Test
    void testNotEqualsNull() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        assertFalse(a.equals(null));
    }
    
    @Test
    void testNotEqualsDifferentClass() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        assertFalse(a.equals("str"));
    }
    
    @Test
    void testNotEqualsDifferentIp() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip1", "type", "app", "beta", "md5");
        ConfigRequestInfo b = new ConfigRequestInfo("ip2", "type", "app", "beta", "md5");
        assertNotEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigRequestInfo a = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        ConfigRequestInfo b = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    @Test
    void testToString() {
        ConfigRequestInfo info = new ConfigRequestInfo("ip", "type", "app", "beta", "md5");
        String str = info.toString();
        assertTrue(str.contains("ip"));
        assertTrue(str.contains("app"));
    }
}
