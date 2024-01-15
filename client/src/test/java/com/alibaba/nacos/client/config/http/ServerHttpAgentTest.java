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

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class ServerHttpAgentTest {
    
    @Mock
    private ServerListManager serverListManager;
    
    private Properties properties;
    
    ServerHttpAgent serverHttpAgent;
    
    @Before
    public void setUp() throws Exception {
        properties = new Properties();
    }
    
    @After
    public void tearDown() throws Exception {
        SpasAdapter.freeCredentialInstance();
        System.clearProperty("spas.identity");
    }
    
    @Test
    public void testInitAkSkFromProperties() throws NoSuchFieldException, IllegalAccessException {
        properties.setProperty(PropertyKeyConst.ACCESS_KEY, "akProperties");
        properties.setProperty(PropertyKeyConst.SECRET_KEY, "skProperties");
        serverHttpAgent = new ServerHttpAgent(serverListManager, properties);
        assertEquals("akProperties", getStringFieldValue("accessKey"));
        assertEquals("skProperties", getStringFieldValue("secretKey"));
    }
    
    @Test
    public void testInitAkSkFromSpasProperties() throws NoSuchFieldException, IllegalAccessException {
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        serverHttpAgent = new ServerHttpAgent(serverListManager, properties);
        assertEquals("akFromSpas", getStringFieldValue("accessKey"));
        assertEquals("skFromSpas", getStringFieldValue("secretKey"));
    }
    
    @Test
    public void testInitAkSkFromPropertiesWithUseRamInfoParsingFalse() throws NoSuchFieldException, IllegalAccessException {
        properties.setProperty(PropertyKeyConst.ACCESS_KEY, "akProperties");
        properties.setProperty(PropertyKeyConst.SECRET_KEY, "skProperties");
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        serverHttpAgent = new ServerHttpAgent(serverListManager, properties);
        assertEquals("akProperties", getStringFieldValue("accessKey"));
        assertEquals("skProperties", getStringFieldValue("secretKey"));
    }
    
    @Test
    public void testInitAkSkFromSpasWithUseRamInfoParsingFalse() throws NoSuchFieldException, IllegalAccessException {
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        serverHttpAgent = new ServerHttpAgent(serverListManager, properties);
        assertNull(getStringFieldValue("accessKey"));
        assertNull(getStringFieldValue("secretKey"));
    }
    
    private String getStringFieldValue(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = ServerHttpAgent.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(serverHttpAgent);
    }
}