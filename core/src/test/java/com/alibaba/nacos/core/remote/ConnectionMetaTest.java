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
import com.alibaba.nacos.api.remote.RemoteConstants;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionMetaTest {
    
    @Test
    void testGetLabelAndGetTag() {
        Map<String, String> labels = new HashMap<>();
        labels.put("k1", "v1");
        labels.put(Constants.VIPSERVER_TAG, "vipTag");
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", labels);
        assertEquals("v1", meta.getLabel("k1"));
        assertEquals("vipTag", meta.getTag());
        assertNull(meta.getLabel("absent"));
    }
    
    @Test
    void testIsSdkSourceAndIsClusterSource() {
        Map<String, String> labelsSdk = new HashMap<>();
        labelsSdk.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        ConnectionMeta metaSdk = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", labelsSdk);
        assertTrue(metaSdk.isSdkSource());
        assertFalse(metaSdk.isClusterSource());
        
        Map<String, String> labelsCluster = new HashMap<>();
        labelsCluster.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_CLUSTER);
        ConnectionMeta metaCluster =
            new ConnectionMeta("id2", "127.0.0.1", "127.0.0.1", 8080, 18080,
                "grpc", "3.0.0", "app", labelsCluster);
        assertFalse(metaCluster.isSdkSource());
        assertTrue(metaCluster.isClusterSource());
        
        ConnectionMeta metaNone = new ConnectionMeta("id3", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        assertFalse(metaNone.isSdkSource());
        assertFalse(metaNone.isClusterSource());
    }
    
    @Test
    void testGetAppLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put(Constants.APPNAME, "myApp");
        labels.put(Constants.APP_CONN_PREFIX + "key1", "value1");
        labels.put(Constants.APP_CONN_PREFIX + "key2", "value2");
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "2.0.0", "app", labels);
        Map<String, String> appLabels = meta.getAppLabels();
        assertEquals("myApp", appLabels.get(Constants.APPNAME));
        assertEquals("2.0.0", appLabels.get(Constants.CLIENT_VERSION_KEY));
        assertEquals("value1", appLabels.get("key1"));
        assertEquals("value2", appLabels.get("key2"));
    }
    
    @Test
    void testSetLabels() {
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        Map<String, String> newLabels = new HashMap<>();
        newLabels.put("a", "b");
        meta.setLabels(newLabels);
        assertEquals(newLabels, meta.getLabels());
    }
    
    @Test
    void testRecordPushQueueBlockTimesAndClearAndLastOver() {
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        meta.recordPushQueueBlockTimes();
        assertFalse(meta.pushQueueBlockTimesLastOver(100_000L));
        meta.recordPushQueueBlockTimes();
        meta.recordPushQueueBlockTimes();
        meta.clearPushQueueBlockTimes();
        assertFalse(meta.pushQueueBlockTimesLastOver(1L));
        meta.recordPushQueueBlockTimes();
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        meta.recordPushQueueBlockTimes();
        assertTrue(meta.pushQueueBlockTimesLastOver(1L));
    }
    
    @Test
    void testTlsProtected() {
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        assertFalse(meta.isTlsProtected());
        meta.setTlsProtected(true);
        assertTrue(meta.isTlsProtected());
    }
    
    @Test
    void testSettersAndGetters() {
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        Date d = new Date();
        meta.setCreateTime(d);
        assertEquals(d, meta.getCreateTime());
        meta.setLastActiveTime(12345L);
        assertEquals(12345L, meta.getLastActiveTime());
        meta.setConnectionId("newId");
        assertEquals("newId", meta.getConnectionId());
        meta.setClientIp("1.1.1.1");
        assertEquals("1.1.1.1", meta.getClientIp());
        meta.setConnectType("http");
        assertEquals("http", meta.getConnectType());
        meta.setVersion("1.0");
        assertEquals("1.0", meta.getVersion());
        meta.setLocalPort(9999);
        assertEquals(9999, meta.getLocalPort());
        meta.setAppName("newApp");
        assertEquals("newApp", meta.getAppName());
        meta.setNamespaceId("ns1");
        assertEquals("ns1", meta.getNamespaceId());
    }
    
    @Test
    void testToString() {
        ConnectionMeta meta = new ConnectionMeta("id", "127.0.0.1", "127.0.0.1", 8080, 18080,
            "grpc", "3.0.0", "app", new HashMap<>());
        String s = meta.toString();
        assertTrue(s.contains("ConnectionMeta"));
        assertTrue(s.contains("connectionId='id'"));
        assertTrue(s.contains("clientIp='127.0.0.1'"));
    }
}
