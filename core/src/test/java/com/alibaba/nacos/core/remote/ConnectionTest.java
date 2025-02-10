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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.response.ConnectionInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTest {
    
    ConnectionMeta connectionMeta;
    
    Connection connection;
    
    @BeforeEach
    void setUp() {
        connectionMeta = new ConnectionMeta("1739168690942_127.0.0.1_18080", "127.0.0.1", "127.0.0.1", 8080, 18080,
                "grpc", "3.0.0", "test", Collections.singletonMap(Constants.APPNAME, "test"));
        connectionMeta.setNamespaceId("public");
        connection = new GrpcConnection(connectionMeta, null, null);
        connection.setAbilityTable(Collections.emptyMap());
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    public void testSerialize() {
        String json = JacksonUtils.toJson(connection);
        assertTrue(json.contains("\"traced\":false"));
        assertTrue(json.contains("\"metaInfo\":{"));
        assertTrue(json.contains("\"connectType\":\"grpc\""));
        assertTrue(json.contains("\"clientIp\":\"127.0.0.1\""));
        assertTrue(json.contains("\"remoteIp\":\"127.0.0.1\""));
        assertTrue(json.contains("\"remotePort\":8080"));
        assertTrue(json.contains("\"localPort\":18080"));
        assertTrue(json.contains("\"version\":\"3.0.0\""));
        assertTrue(json.contains("\"connectionId\":\"1739168690942_127.0.0.1_18080\""));
        assertTrue(json.contains("\"createTime\":" + connection.getMetaInfo().getCreateTime().getTime()));
        assertTrue(json.contains("\"lastActiveTime\":" + connection.getMetaInfo().getLastActiveTime()));
        assertTrue(json.contains("\"appName\":\"test\""));
        assertTrue(json.contains("\"labels\":{"));
        assertTrue(json.contains("\"AppName\":\"test\""));
        assertTrue(json.contains("\"sdkSource\":false"));
        assertTrue(json.contains("\"clusterSource\":false"));
        assertTrue(json.contains("\"connected\":false"));
        assertTrue(json.contains("\"abilityTable\":{}"));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
    }
    
    @Test
    public void testDeserializeToConnectionInfo() {
        String json = JacksonUtils.toJson(connection);
        ConnectionInfo connectionInfo = JacksonUtils.toObj(json, ConnectionInfo.class);
        assertEquals(connection.isTraced(), connectionInfo.isTraced());
        assertEquals(connection.getAbilityTable(), connectionInfo.getAbilityTable());
        assertEquals(connection.getMetaInfo().getConnectType(), connectionInfo.getMetaInfo().getConnectType());
        assertEquals(connection.getMetaInfo().getClientIp(), connectionInfo.getMetaInfo().getClientIp());
        assertEquals(connection.getMetaInfo().getRemoteIp(), connectionInfo.getMetaInfo().getRemoteIp());
        assertEquals(connection.getMetaInfo().getRemotePort(), connectionInfo.getMetaInfo().getRemotePort());
        assertEquals(connection.getMetaInfo().getLocalPort(), connectionInfo.getMetaInfo().getLocalPort());
        assertEquals(connection.getMetaInfo().getVersion(), connectionInfo.getMetaInfo().getVersion());
        assertEquals(connection.getMetaInfo().getConnectionId(), connectionInfo.getMetaInfo().getConnectionId());
        assertEquals(connection.getMetaInfo().getCreateTime().getTime(),
                connectionInfo.getMetaInfo().getCreateTime().getTime());
        assertEquals(connection.getMetaInfo().getLastActiveTime(), connectionInfo.getMetaInfo().getLastActiveTime());
        assertEquals(connection.getMetaInfo().getAppName(), connectionInfo.getMetaInfo().getAppName());
        assertEquals(connection.getMetaInfo().getLabels(), connectionInfo.getMetaInfo().getLabels());
        assertEquals(connection.getMetaInfo().getNamespaceId(), connectionInfo.getMetaInfo().getNamespaceId());
    }
}