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

package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.common.utils.RandomUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultParamCheckerTest {
    
    DefaultParamChecker paramChecker;
    
    int maxMetadataLength = RandomUtils.nextInt(1024, 10240);
    
    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("nacos.naming.service.metadata.length", String.valueOf(maxMetadataLength));
        paramChecker = new DefaultParamChecker();
    }
    
    @Test
    void testCheckerType() {
        assertEquals("default", paramChecker.getCheckerType());
    }
    
    @Test
    void testCheckEmptyParamInfoList() {
        ParamCheckResponse actual = paramChecker.checkParamInfoList(null);
        assertTrue(actual.isSuccess());
        actual = paramChecker.checkParamInfoList(Collections.emptyList());
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckEmptyParamInfo() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        paramInfos.add(null);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForNamespaceShowName() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String namespaceShowName = buildStringLength(257);
        paramInfo.setNamespaceShowName(namespaceShowName);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'namespaceShowName' is illegal, the param length should not exceed 256.", actual.getMessage());
        // Pattern
        paramInfo.setNamespaceShowName("hsbfkj@$!#khdkad");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'namespaceShowName' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setNamespaceShowName("测试");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForNamespaceId() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String namespaceId = buildStringLength(65);
        paramInfo.setNamespaceId(namespaceId);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'namespaceId/tenant' is illegal, the param length should not exceed 64.", actual.getMessage());
        // Pattern
        paramInfo.setNamespaceId("hsbfkj@$!#khdkad");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'namespaceId/tenant' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setNamespaceId("123-ashdal");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForDataId() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String dataId = buildStringLength(257);
        paramInfo.setDataId(dataId);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'dataId' is illegal, the param length should not exceed 256.", actual.getMessage());
        // Pattern
        paramInfo.setDataId("hsbfkj@$!#khdkad");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'dataId' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setDataId("a-zA-Z0-9-_:.");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForServiceName() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String serviceName = buildStringLength(513);
        paramInfo.setServiceName(serviceName);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'serviceName' is illegal, the param length should not exceed 512.", actual.getMessage());
        // Pattern
        paramInfo.setServiceName("@hsbfkj$@@!#khdkad啊");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'serviceName' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setServiceName("com.aaa@bbb#_{}-b:v1.2.2");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForGroup() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String group = buildStringLength(129);
        paramInfo.setGroup(group);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'group' is illegal, the param length should not exceed 128.", actual.getMessage());
        // Pattern
        paramInfo.setGroup("@hsbfkj$@@!#khdkad啊@@");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'group' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setGroup("a-zA-Z0-9-_:.");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForClusters() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String cluster = buildStringLength(65);
        paramInfo.setClusters(cluster + "," + cluster);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'cluster' is illegal, the param length should not exceed 64.", actual.getMessage());
        // Pattern
        paramInfo.setClusters("@hsbfkj$@@!#khdkad啊@@");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'cluster' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setClusters("0-9a-zA-Z-_,DEFAULT_abc-100");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForCluster() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String cluster = buildStringLength(65);
        paramInfo.setCluster(cluster);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'cluster' is illegal, the param length should not exceed 64.", actual.getMessage());
        // Pattern
        paramInfo.setCluster("@hsbfkj$@@!#khdkad啊@@");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'cluster' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setCluster("0-9a-zA-Z-_");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForIp() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Max Length
        String ip = buildStringLength(129);
        paramInfo.setIp(ip);
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'ip' is illegal, the param length should not exceed 128.", actual.getMessage());
        // Pattern
        paramInfo.setIp("禁止中文");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'ip' is illegal, illegal characters should not appear in the param.", actual.getMessage());
        // Success
        paramInfo.setIp("host_or_domain_or_ipv4_or_ipv6");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForPort() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        // Negative port
        paramInfo.setPort("-1");
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'port' is illegal, the value should be between 0 and 65535.", actual.getMessage());
        // Over than range
        paramInfo.setPort("65536");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'port' is illegal, the value should be between 0 and 65535.", actual.getMessage());
        // Not number
        paramInfo.setPort("port");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals("Param 'port' is illegal, the value should be between 0 and 65535.", actual.getMessage());
        // Success
        paramInfo.setPort("8848");
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    @Test
    void testCheckParamInfoForMetadata() {
        ParamInfo paramInfo = new ParamInfo();
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        Map<String, String> metadata = new HashMap<>();
        paramInfo.setMetadata(metadata);
        // Max length
        metadata.put("key1", "");
        metadata.put("key2", buildStringLength(maxMetadataLength));
        ParamCheckResponse actual = paramChecker.checkParamInfoList(paramInfos);
        assertFalse(actual.isSuccess());
        assertEquals(String.format("Param 'Metadata' is illegal, the param length should not exceed %d.", maxMetadataLength), actual.getMessage());
        // Success
        metadata.put("key2", String.format("Any key and value, only require length sum not more than %d.", maxMetadataLength));
        actual = paramChecker.checkParamInfoList(paramInfos);
        assertTrue(actual.isSuccess());
    }
    
    private String buildStringLength(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append("a");
        }
        return builder.toString();
    }
}