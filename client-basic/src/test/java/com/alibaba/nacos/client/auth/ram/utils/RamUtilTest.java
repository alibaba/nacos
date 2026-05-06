/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.auth.ram.identify.CredentialService;
import com.alibaba.nacos.client.auth.ram.identify.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RamUtilTest {
    
    private Properties properties;
    
    @BeforeEach
    public void setUp() throws Exception {
        SpasAdapter.freeCredentialInstance();
        Credentials credentials = new Credentials("spasAk", "spasSk", "spasNamespaceId");
        CredentialService.getInstance().setStaticCredential(credentials);
        properties = new Properties();
        properties.setProperty(PropertyKeyConst.ACCESS_KEY, "userAk");
        properties.setProperty(PropertyKeyConst.SECRET_KEY, "userSk");
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        SpasAdapter.freeCredentialInstance();
    }
    
    @Test
    public void testGetAccessKeyWithUserAkSk() {
        assertEquals("userAk", RamUtil.getAccessKey(properties));
        assertEquals("userSk", RamUtil.getSecretKey(properties));
    }
    
    @Test
    public void testGetAccessKeyWithSpasAkSk() {
        assertEquals("spasAk", RamUtil.getAccessKey(new Properties()));
        assertEquals("spasSk", RamUtil.getSecretKey(new Properties()));
    }
    
    @Test
    public void testGetAccessKeyWithoutSpasAkSk() {
        Properties properties1 = new Properties();
        properties1.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        assertNull(RamUtil.getAccessKey(properties1));
        assertNull(RamUtil.getSecretKey(properties1));
    }
}