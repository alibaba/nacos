/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v1;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientInfoTest {
    
    private final String testVersionString = "2.0.0-ALPHA";
    
    @Test
    public void testGetClientInfoForJava() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.JAVA_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.JAVA, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForGo() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.GO_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.GO, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForC() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.C_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.C, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForCpp() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.CPP_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.C, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForDns() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.DNSF_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.DNS, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForSdk() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.SDK_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.JAVA_SDK, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForServer() {
        String userAgent = getUserAgent(UtilsAndCommons.NACOS_SERVER_HEADER);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.NACOS_SERVER, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForNginx() {
        String userAgent = getUserAgent(ClientInfo.ClientTypeDescription.NGINX_CLIENT);
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.TENGINE, actual.type);
        assertEquals(testVersionString, actual.version.toString());
    }
    
    @Test
    public void testGetClientInfoForUnknown() {
        String userAgent = getUserAgent("TestClient");
        ClientInfo actual = new ClientInfo(userAgent);
        assertEquals(ClientInfo.ClientType.UNKNOWN, actual.type);
        assertEquals("0.0.0", actual.version.toString());
    }
    
    private String getUserAgent(String client) {
        return client + ":v" + testVersionString;
    }
}
