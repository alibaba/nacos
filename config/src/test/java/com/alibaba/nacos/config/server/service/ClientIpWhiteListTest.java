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

package com.alibaba.nacos.config.server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpWhiteListTest {
    
    @AfterEach
    void tearDown() {
        ClientIpWhiteList.load("");
    }
    
    @Test
    void testIsOpenFieldIsVolatile() throws NoSuchFieldException {
        Field isOpenField = ClientIpWhiteList.class.getDeclaredField("isOpen");
        assertTrue(Modifier.isVolatile(isOpenField.getModifiers()));
    }
    
    @Test
    void testLoadValidContentEnablesWhitelist() {
        String content = "{\"isOpen\":true,\"ips\":[\"192.168.1.1\",\"10.0.0.1\"]}";
        ClientIpWhiteList.load(content);
        assertTrue(ClientIpWhiteList.isEnableWhitelist());
        assertTrue(ClientIpWhiteList.isLegalClient("192.168.1.1"));
        assertTrue(ClientIpWhiteList.isLegalClient("10.0.0.1"));
        assertFalse(ClientIpWhiteList.isLegalClient("172.16.0.1"));
    }
    
    @Test
    void testLoadBlankContentDisablesWhitelist() {
        String content = "{\"isOpen\":true,\"ips\":[\"192.168.1.1\"]}";
        ClientIpWhiteList.load(content);
        assertTrue(ClientIpWhiteList.isEnableWhitelist());
        
        ClientIpWhiteList.load("");
        assertFalse(ClientIpWhiteList.isEnableWhitelist());
    }
    
    @Test
    void testLoadNullIsOpenDoesNotThrowNpe() {
        String content = "{\"ips\":[\"192.168.1.1\"]}";
        assertDoesNotThrow(() -> ClientIpWhiteList.load(content));
        assertFalse(ClientIpWhiteList.isEnableWhitelist());
    }
    
    @Test
    void testLoadWithIsOpenFalse() {
        String content = "{\"isOpen\":false,\"ips\":[\"192.168.1.1\"]}";
        ClientIpWhiteList.load(content);
        assertFalse(ClientIpWhiteList.isEnableWhitelist());
    }
    
    @Test
    void testIsLegalClientThrowsOnBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> ClientIpWhiteList.isLegalClient(""));
        assertThrows(IllegalArgumentException.class, () -> ClientIpWhiteList.isLegalClient(null));
    }
    
    @Test
    void testLoadInvalidJsonDoesNotCrash() {
        assertDoesNotThrow(() -> ClientIpWhiteList.load("not-valid-json"));
        assertFalse(ClientIpWhiteList.isEnableWhitelist());
    }
}
