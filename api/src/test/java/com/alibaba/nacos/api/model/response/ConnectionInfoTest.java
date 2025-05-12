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

package com.alibaba.nacos.api.model.response;

import com.alibaba.nacos.api.common.Constants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionInfoTest {
    
    private ObjectMapper mapper;
    
    ConnectionInfo connectionInfo;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ConnectionMetaInfo metaInfo = new ConnectionMetaInfo();
        metaInfo.setConnectType("grpc");
        metaInfo.setClientIp("127.0.0.1");
        metaInfo.setRemoteIp("127.0.0.1");
        metaInfo.setRemotePort(8080);
        metaInfo.setLocalPort(18080);
        metaInfo.setVersion("3.0.0");
        metaInfo.setConnectionId("1739168690942_127.0.0.1_18080");
        Date now = new Date();
        metaInfo.setCreateTime(now);
        metaInfo.setLastActiveTime(now.getTime());
        metaInfo.setLabels(Collections.singletonMap(Constants.APPNAME, "test"));
        metaInfo.setAppName("test");
        metaInfo.setNamespaceId("public");
        connectionInfo = new ConnectionInfo();
        connectionInfo.setMetaInfo(metaInfo);
        connectionInfo.setAbilityTable(Collections.emptyMap());
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void testSerialize() throws Exception {
        String json = mapper.writeValueAsString(connectionInfo);
        assertTrue(json.contains("\"traced\":false"));
        assertTrue(json.contains("\"metaInfo\":{"));
        assertTrue(json.contains("\"connectType\":\"grpc\""));
        assertTrue(json.contains("\"clientIp\":\"127.0.0.1\""));
        assertTrue(json.contains("\"remoteIp\":\"127.0.0.1\""));
        assertTrue(json.contains("\"remotePort\":8080"));
        assertTrue(json.contains("\"localPort\":18080"));
        assertTrue(json.contains("\"version\":\"3.0.0\""));
        assertTrue(json.contains("\"connectionId\":\"1739168690942_127.0.0.1_18080\""));
        assertTrue(json.contains("\"createTime\":" + connectionInfo.getMetaInfo().getCreateTime().getTime()));
        assertTrue(json.contains("\"lastActiveTime\":" + connectionInfo.getMetaInfo().getLastActiveTime()));
        assertTrue(json.contains("\"appName\":\"test\""));
        assertTrue(json.contains("\"labels\":{"));
        assertTrue(json.contains("\"AppName\":\"test\""));
        assertTrue(json.contains("\"abilityTable\":{}"));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        // full connection information from server.
        String json = "{\"traced\":false,\"abilityTable\":{},\"metaInfo\":{\"namespaceId\":\"public\",\"connectType\":\"grpc\","
                + "\"clientIp\":\"127.0.0.1\",\"remoteIp\":\"127.0.0.1\",\"remotePort\":8080,"
                + "\"localPort\":18080,\"version\":\"3.0.0\",\"connectionId\":\"1739168690942_127.0.0.1_18080\","
                + "\"createTime\":1739170615198,\"lastActiveTime\":1739170615198,\"appName\":\"test\","
                + "\"labels\":{\"AppName\":\"test\"},\"appLabels\":{\"ClientVersion\":\"3.0.0\",\"AppName\":\"test\"},"
                + "\"sdkSource\":false,\"clusterSource\":false},\"connected\":false,\"labels\":{\"AppName\":\"test\"},"
                + "\"appLabels\":{\"ClientVersion\":\"3.0.0\",\"AppName\":\"test\"}}";
        ConnectionInfo actualConnectionInfo = mapper.readValue(json, ConnectionInfo.class);
        assertEquals(connectionInfo.isTraced(), actualConnectionInfo.isTraced());
        assertEquals(connectionInfo.getAbilityTable(), actualConnectionInfo.getAbilityTable());
        assertEquals(connectionInfo.getMetaInfo().getConnectType(),
                actualConnectionInfo.getMetaInfo().getConnectType());
        assertEquals(connectionInfo.getMetaInfo().getClientIp(), actualConnectionInfo.getMetaInfo().getClientIp());
        assertEquals(connectionInfo.getMetaInfo().getRemoteIp(), actualConnectionInfo.getMetaInfo().getRemoteIp());
        assertEquals(connectionInfo.getMetaInfo().getRemotePort(), actualConnectionInfo.getMetaInfo().getRemotePort());
        assertEquals(connectionInfo.getMetaInfo().getLocalPort(), actualConnectionInfo.getMetaInfo().getLocalPort());
        assertEquals(connectionInfo.getMetaInfo().getVersion(), actualConnectionInfo.getMetaInfo().getVersion());
        assertEquals(connectionInfo.getMetaInfo().getConnectionId(),
                actualConnectionInfo.getMetaInfo().getConnectionId());
        assertEquals(1739170615198L, actualConnectionInfo.getMetaInfo().getCreateTime().getTime());
        assertEquals(1739170615198L, actualConnectionInfo.getMetaInfo().getLastActiveTime());
        assertEquals(connectionInfo.getMetaInfo().getAppName(), actualConnectionInfo.getMetaInfo().getAppName());
        assertEquals(connectionInfo.getMetaInfo().getLabels(), actualConnectionInfo.getMetaInfo().getLabels());
        assertEquals(connectionInfo.getMetaInfo().getNamespaceId(),
                actualConnectionInfo.getMetaInfo().getNamespaceId());
    }
}