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

class ConfigInfoTest {
    
    private ObjectMapper mapper;
    
    private ConfigBasicInfo basicInfo;
    
    private ConfigDetailInfo detailInfo;
    
    private ConfigGrayInfo grayInfo;
    
    private long createTime;
    
    private long modifyTime;
    
    @BeforeEach
    void setUp() {
        createTime = System.currentTimeMillis();
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        basicInfo = new ConfigBasicInfo();
        detailInfo = new ConfigDetailInfo();
        grayInfo = new ConfigGrayInfo();
        modifyTime = System.currentTimeMillis();
        mockBasicInfo(basicInfo, createTime, modifyTime);
        mockBasicInfo(detailInfo, createTime, modifyTime);
        mockBasicInfo(grayInfo, createTime, modifyTime);
        mockDetailInfo(detailInfo);
        mockDetailInfo(grayInfo);
        grayInfo.setGrayName("testGrayName");
        grayInfo.setGrayRule(
                "{\"type\":\"beta\",\"version\":\"1.0.0\",\"expr\":\"127.0.0.1,127.0.0.2\",\"priority\":-1000}");
    }
    
    private void mockBasicInfo(ConfigBasicInfo basicInfo, long createTime, long modifyTime) {
        basicInfo.setId(1L);
        basicInfo.setNamespaceId("testNs");
        basicInfo.setGroupName("testGroup");
        basicInfo.setDataId("testDataId");
        basicInfo.setMd5("testMd5");
        basicInfo.setType("text");
        basicInfo.setAppName("testApp");
        basicInfo.setCreateTime(createTime);
        basicInfo.setModifyTime(modifyTime);
        basicInfo.setDesc("testDesc");
        basicInfo.setConfigTags("testConfigTag1,testConfigTag2");
    }
    
    private void mockDetailInfo(ConfigDetailInfo detailInfo) {
        detailInfo.setContent("testContent");
        detailInfo.setEncryptedDataKey("testEncryptedDataKey");
        detailInfo.setCreateUser("testCreateUser");
        detailInfo.setCreateIp("1.1.1.1");
    }
    
    @Test
    public void testBasicInfoSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(basicInfo);
        assertJsonContainBasicInfos(json);
    }
    
    @Test
    public void testBasicInfoDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"1\",\"namespaceId\":\"testNs\",\"groupName\":\"testGroup\",\"dataId\":\"testDataId\","
                + "\"md5\":\"testMd5\",\"type\":\"text\",\"appName\":\"testApp\",\"createTime\":%s,\"modifyTime\":%s}";
        json = String.format(json, createTime, modifyTime);
        assertBasicInfo(mapper.readValue(json, ConfigBasicInfo.class));
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
                + "\"md5\":\"testMd5\",\"type\":\"text\",\"appName\":\"testApp\",\"createTime\":%s,"
                + "\"modifyTime\":%s,\"content\":\"testContent\",\"desc\":\"testDesc\","
                + "\"encryptedDataKey\":\"testEncryptedDataKey\",\"createUser\":\"testCreateUser\","
                + "\"createIp\":\"1.1.1.1\",\"configTags\":\"testConfigTag1,testConfigTag2\"}";
        json = String.format(json, createTime, modifyTime);
        ConfigDetailInfo detailInfo = mapper.readValue(json, ConfigDetailInfo.class);
        assertBasicInfo(detailInfo);
        assertDetailInfo(detailInfo);
    }
    
    @Test
    public void testGrayInfoSerialize() throws JsonProcessingException {
        String json = mapper.writeValueAsString(grayInfo);
        assertJsonContainBasicInfos(json);
        asserJsonContainDetailInfos(json);
        assertTrue(json.contains("\"grayName\":\"testGrayName\""));
        assertTrue(json.contains("\"grayRule\":\"{"));
        assertTrue(json.contains("\\\"type\\\":\\\"beta\\\""));
        assertTrue(json.contains("\\\"version\\\":\\\"1.0.0\\\""));
        assertTrue(json.contains("\\\"expr\\\":\\\"127.0.0.1,127.0.0.2\\\""));
        assertTrue(json.contains("\\\"priority\\\":-1000"));
    }
    
    @Test
    public void testGrayInfoDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"1\",\"namespaceId\":\"testNs\",\"groupName\":\"testGroup\",\"dataId\":\"testDataId\","
                + "\"md5\":\"testMd5\",\"type\":\"text\",\"appName\":\"testApp\",\"createTime\":%s,\"modifyTime\":%s,"
                + "\"content\":\"testContent\",\"desc\":\"testDesc\",\"encryptedDataKey\":\"testEncryptedDataKey\","
                + "\"createUser\":\"testCreateUser\",\"createIp\":\"1.1.1.1\",\"configTags\":\"testConfigTag1,testConfigTag2\","
                + "\"grayName\":\"testGrayName\",\"grayRule\":"
                + "\"{\\\"type\\\":\\\"beta\\\",\\\"version\\\":\\\"1.0.0\\\",\\\"expr\\\":\\\"127.0.0.1,127.0.0.2\\\",\\\"priority\\\":-1000}\"}";
        json = String.format(json, createTime, modifyTime);
        ConfigGrayInfo actualGrayInfo = mapper.readValue(json, ConfigGrayInfo.class);
        assertBasicInfo(actualGrayInfo);
        assertDetailInfo(actualGrayInfo);
        assertEquals(grayInfo.getGrayName(), actualGrayInfo.getGrayName());
        assertEquals(grayInfo.getGrayRule(), actualGrayInfo.getGrayRule());
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
    }
    
    private void asserJsonContainDetailInfos(String json) {
        assertTrue(json.contains("\"content\":\"testContent\""));
        assertTrue(json.contains("\"desc\":\"testDesc\""));
        assertTrue(json.contains("\"encryptedDataKey\":\"testEncryptedDataKey\""));
        assertTrue(json.contains("\"createUser\":\"testCreateUser\""));
        assertTrue(json.contains("\"createIp\":\"1.1.1.1\""));
        assertTrue(json.contains("\"configTags\":\"testConfigTag1,testConfigTag2\""));
    }
    
    private void assertBasicInfo(ConfigBasicInfo actual) {
        assertEquals(basicInfo.getId(), actual.getId());
        assertEquals(basicInfo.getNamespaceId(), actual.getNamespaceId());
        assertEquals(basicInfo.getGroupName(), actual.getGroupName());
        assertEquals(basicInfo.getDataId(), actual.getDataId());
        assertEquals(basicInfo.getMd5(), actual.getMd5());
        assertEquals(basicInfo.getType(), actual.getType());
        assertEquals(basicInfo.getAppName(), actual.getAppName());
        assertEquals(basicInfo.getCreateTime(), actual.getCreateTime());
        assertEquals(basicInfo.getModifyTime(), actual.getModifyTime());
    }
    
    private void assertDetailInfo(ConfigDetailInfo actual) {
        assertEquals(detailInfo.getContent(), actual.getContent());
        assertEquals(detailInfo.getDesc(), actual.getDesc());
        assertEquals(detailInfo.getEncryptedDataKey(), actual.getEncryptedDataKey());
        assertEquals(detailInfo.getCreateUser(), actual.getCreateUser());
        assertEquals(detailInfo.getCreateIp(), actual.getCreateIp());
        assertEquals(detailInfo.getConfigTags(), actual.getConfigTags());
    }
}