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

package com.alibaba.nacos.api.config.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigHistoryInfoTest {
    
    private ObjectMapper mapper;
    
    private ConfigHistoryBasicInfo basicInfo;
    
    private ConfigHistoryDetailInfo detailInfo;
    
    private long createTime;
    
    private long modifyTime;
    
    @BeforeEach
    void setUp() {
        createTime = System.currentTimeMillis();
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        basicInfo = new ConfigHistoryBasicInfo();
        detailInfo = new ConfigHistoryDetailInfo();
        modifyTime = System.currentTimeMillis();
        mockBasicInfo(basicInfo, createTime, modifyTime);
        mockBasicInfo(detailInfo, createTime, modifyTime);
        mockDetailInfo(detailInfo);
    }
    
    private void mockBasicInfo(ConfigHistoryBasicInfo basicInfo, long createTime, long modifyTime) {
        basicInfo.setId(1L);
        basicInfo.setNamespaceId("testNs");
        basicInfo.setGroupName("testGroup");
        basicInfo.setDataId("testDataId");
        basicInfo.setMd5("testMd5");
        basicInfo.setType("text");
        basicInfo.setAppName("testApp");
        basicInfo.setCreateTime(createTime);
        basicInfo.setModifyTime(modifyTime);
        basicInfo.setSrcIp("1.1.1.1");
        basicInfo.setSrcUser("testCreateUser");
        basicInfo.setOpType("I");
        basicInfo.setPublishType("formal");
    }
    
    private void mockDetailInfo(ConfigHistoryDetailInfo detailInfo) {
        detailInfo.setContent("testContent");
        detailInfo.setEncryptedDataKey("testEncryptedDataKey");
        detailInfo.setGrayName("testGrayName");
        detailInfo.setExtInfo("{\"type\":\"text\"}");
    }
    
    @Test
    public void testBasicInfoSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(basicInfo);
        assertJsonContainBasicInfos(json);
    }
    
    @Test
    public void testBasicInfoDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"1\",\"namespaceId\":\"testNs\",\"groupName\":\"testGroup\",\"dataId\":\"testDataId\","
                + "\"md5\":\"testMd5\",\"type\":\"text\",\"appName\":\"testApp\",\"createTime\":%s,\"modifyTime\":%s,"
                + "\"srcIp\":\"1.1.1.1\",\"srcUser\":\"testCreateUser\",\"opType\":\"I\",\"publishType\":\"formal\"}";
        json = String.format(json, createTime, modifyTime);
        assertBasicInfo(mapper.readValue(json, ConfigHistoryBasicInfo.class));
    }
    
    @Test
    public void testDetailInfoSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(detailInfo);
        assertJsonContainBasicInfos(json);
        asserJsonContainDetailInfos(json);
    }
    
    @Test
    public void testDetailInfoDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"1\",\"namespaceId\":\"testNs\",\"groupName\":\"testGroup\",\"dataId\":\"testDataId\","
                + "\"md5\":\"testMd5\",\"type\":\"text\",\"appName\":\"testApp\",\"createTime\":%s,\"modifyTime\":%s,"
                + "\"srcIp\":\"1.1.1.1\",\"srcUser\":\"testCreateUser\",\"opType\":\"I\",\"publishType\":\"formal\","
                + "\"content\":\"testContent\",\"encryptedDataKey\":\"testEncryptedDataKey\",\"grayName\":\"testGrayName\","
                + "\"extInfo\":\"{\\\"type\\\":\\\"text\\\"}\"}";
        json = String.format(json, createTime, modifyTime);
        ConfigHistoryDetailInfo detailInfo = mapper.readValue(json, ConfigHistoryDetailInfo.class);
        assertBasicInfo(detailInfo);
        assertDetailInfo(detailInfo);
    }
    
    private void assertJsonContainBasicInfos(String json) {
        assertTrue(json.contains("\"id\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"testNs\""));
        assertTrue(json.contains("\"groupName\":\"testGroup\""));
        assertTrue(json.contains("\"dataId\":\"testDataId\""));
        assertTrue(json.contains("\"md5\":\"testMd5\""));
        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("\"appName\":\"testApp\""));
        assertTrue(json.contains("\"createTime\":" + createTime));
        assertTrue(json.contains("\"modifyTime\":" + modifyTime));
        assertTrue(json.contains("\"srcIp\":\"1.1.1.1\""));
        assertTrue(json.contains("\"srcUser\":\"testCreateUser\""));
        assertTrue(json.contains("\"opType\":\"I\""));
        assertTrue(json.contains("\"publishType\":\"formal\""));
    }
    
    private void asserJsonContainDetailInfos(String json) {
        assertTrue(json.contains("\"content\":\"testContent\""));
        assertTrue(json.contains("\"encryptedDataKey\":\"testEncryptedDataKey\""));
        assertTrue(json.contains("\"grayName\":\"testGrayName\""));
        assertTrue(json.contains("\"extInfo\":\"{\\\"type\\\":\\\"text\\\"}\""));
    }
    
    private void assertBasicInfo(ConfigHistoryBasicInfo actual) {
        assertEquals(basicInfo.getId(), actual.getId());
        assertEquals(basicInfo.getNamespaceId(), actual.getNamespaceId());
        assertEquals(basicInfo.getGroupName(), actual.getGroupName());
        assertEquals(basicInfo.getDataId(), actual.getDataId());
        assertEquals(basicInfo.getMd5(), actual.getMd5());
        assertEquals(basicInfo.getType(), actual.getType());
        assertEquals(basicInfo.getAppName(), actual.getAppName());
        assertEquals(basicInfo.getCreateTime(), actual.getCreateTime());
        assertEquals(basicInfo.getModifyTime(), actual.getModifyTime());
        assertEquals(basicInfo.getSrcIp(), actual.getSrcIp());
        assertEquals(basicInfo.getSrcUser(), actual.getSrcUser());
        assertEquals(basicInfo.getPublishType(), actual.getPublishType());
        assertEquals(basicInfo.getOpType(), actual.getOpType());
    }
    
    private void assertDetailInfo(ConfigHistoryDetailInfo actual) {
        assertEquals(detailInfo.getContent(), actual.getContent());
        assertEquals(detailInfo.getEncryptedDataKey(), actual.getEncryptedDataKey());
        assertEquals(detailInfo.getGrayName(), actual.getGrayName());
        assertEquals(detailInfo.getExtInfo(), actual.getExtInfo());
    }
}