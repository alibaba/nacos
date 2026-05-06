/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseUtilTest {
    
    String lineSeparator = System.lineSeparator();
    
    @Test
    void testWriteErrMsg() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseUtil.writeErrMsg(response, 404, "test");
        assertEquals(404, response.getStatus());
        try {
            assertEquals("test" + lineSeparator, response.getContentAsString());
        } catch (UnsupportedEncodingException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testTransferToConfigDetailInfo() {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setId(1L);
        configAllInfo.setTenant("testNs");
        configAllInfo.setGroup(Constants.DEFAULT_GROUP);
        configAllInfo.setDataId("testDs");
        configAllInfo.setMd5("testMd5");
        configAllInfo.setEncryptedDataKey("testEncryptedDataKey");
        configAllInfo.setContent("testContent");
        configAllInfo.setDesc("testDesc");
        configAllInfo.setType("text");
        configAllInfo.setAppName("testAppName");
        configAllInfo.setCreateIp("1.1.1.1");
        configAllInfo.setCreateUser("testCreateUser");
        configAllInfo.setCreateTime(System.currentTimeMillis());
        configAllInfo.setModifyTime(System.currentTimeMillis());
        configAllInfo.setConfigTags("testConfigTag1,testConfigTag2");
        configAllInfo.setUse("testUse");
        configAllInfo.setEffect("testEffect");
        configAllInfo.setSchema("testSchema");
        ConfigDetailInfo configDetailInfo = ResponseUtil.transferToConfigDetailInfo(configAllInfo);
        assertEquals(configAllInfo.getId(), configDetailInfo.getId());
        assertEquals(configAllInfo.getTenant(), configDetailInfo.getNamespaceId());
        assertEquals(configAllInfo.getGroup(), configDetailInfo.getGroupName());
        assertEquals(configAllInfo.getDataId(), configDetailInfo.getDataId());
        assertEquals(configAllInfo.getMd5(), configDetailInfo.getMd5());
        assertEquals(configAllInfo.getEncryptedDataKey(), configDetailInfo.getEncryptedDataKey());
        assertEquals(configAllInfo.getContent(), configDetailInfo.getContent());
        assertEquals(configAllInfo.getDesc(), configDetailInfo.getDesc());
        assertEquals(configAllInfo.getType(), configDetailInfo.getType());
        assertEquals(configAllInfo.getAppName(), configDetailInfo.getAppName());
        assertEquals(configAllInfo.getCreateIp(), configDetailInfo.getCreateIp());
        assertEquals(configAllInfo.getCreateUser(), configDetailInfo.getCreateUser());
        assertEquals(configAllInfo.getCreateTime(), configDetailInfo.getCreateTime());
        assertEquals(configAllInfo.getModifyTime(), configDetailInfo.getModifyTime());
        assertEquals(configAllInfo.getConfigTags(), configDetailInfo.getConfigTags());
    }
    
    @Test
    void testTransferToConfigBasicInfo() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(1L);
        configInfo.setTenant("testNs");
        configInfo.setGroup(Constants.DEFAULT_GROUP);
        configInfo.setDataId("testDs");
        configInfo.setMd5("testMd5");
        configInfo.setEncryptedDataKey("testEncryptedDataKey");
        configInfo.setContent("testContent");
        configInfo.setType("text");
        configInfo.setAppName("testAppName");
        configInfo.setDesc("testDesc");
        configInfo.setConfigTags("tag1,tag2");
        ConfigBasicInfo configBasicInfo = ResponseUtil.transferToConfigBasicInfo(configInfo);
        assertEquals(configInfo.getId(), configBasicInfo.getId());
        assertEquals(configInfo.getTenant(), configBasicInfo.getNamespaceId());
        assertEquals(configInfo.getGroup(), configBasicInfo.getGroupName());
        assertEquals(configInfo.getDataId(), configBasicInfo.getDataId());
        assertEquals(configInfo.getMd5(), configBasicInfo.getMd5());
        assertEquals(configInfo.getType(), configBasicInfo.getType());
        assertEquals(configInfo.getAppName(), configBasicInfo.getAppName());
        assertEquals(configInfo.getDesc(), configBasicInfo.getDesc());
        assertEquals(configInfo.getConfigTags(), configBasicInfo.getConfigTags());
        assertEquals(0L, configBasicInfo.getCreateTime());
        assertEquals(0L, configBasicInfo.getModifyTime());
    }
    
    @Test
    void testTransferToConfigBasicInfoFromWrapper() {
        ConfigInfoWrapper configInfo = new ConfigInfoWrapper();
        configInfo.setId(1L);
        configInfo.setTenant("testNs");
        configInfo.setGroup(Constants.DEFAULT_GROUP);
        configInfo.setDataId("testDs");
        configInfo.setMd5("testMd5");
        configInfo.setEncryptedDataKey("testEncryptedDataKey");
        configInfo.setContent("testContent");
        configInfo.setType("text");
        configInfo.setAppName("testAppName");
        configInfo.setDesc("testDesc");
        configInfo.setConfigTags("tag1,tag2");
        configInfo.setLastModified(System.currentTimeMillis());
        ConfigBasicInfo configBasicInfo = ResponseUtil.transferToConfigBasicInfo(configInfo);
        assertEquals(configInfo.getId(), configBasicInfo.getId());
        assertEquals(configInfo.getTenant(), configBasicInfo.getNamespaceId());
        assertEquals(configInfo.getGroup(), configBasicInfo.getGroupName());
        assertEquals(configInfo.getDataId(), configBasicInfo.getDataId());
        assertEquals(configInfo.getMd5(), configBasicInfo.getMd5());
        assertEquals(configInfo.getType(), configBasicInfo.getType());
        assertEquals(configInfo.getAppName(), configBasicInfo.getAppName());
        assertEquals(configInfo.getDesc(), configBasicInfo.getDesc());
        assertEquals(configInfo.getConfigTags(), configBasicInfo.getConfigTags());
        assertEquals(0L, configBasicInfo.getCreateTime());
        assertEquals(configInfo.getLastModified(), configBasicInfo.getModifyTime());
    }
    
    @Test
    void testTransferToConfigGrayInfo() {
        ConfigInfoGrayWrapper configInfoGray = new ConfigInfoGrayWrapper();
        configInfoGray.setId(1L);
        configInfoGray.setTenant("testNs");
        configInfoGray.setGroup(Constants.DEFAULT_GROUP);
        configInfoGray.setDataId("testDs");
        configInfoGray.setMd5("testMd5");
        configInfoGray.setEncryptedDataKey("testEncryptedDataKey");
        configInfoGray.setContent("testContent");
        configInfoGray.setType("text");
        configInfoGray.setAppName("testAppName");
        configInfoGray.setGrayName("testGrayName");
        configInfoGray.setGrayRule("testGrayRule");
        configInfoGray.setSrcUser("testSrcUser");
        configInfoGray.setLastModified(System.currentTimeMillis());
        ConfigGrayInfo configGrayInfo = ResponseUtil.transferToConfigGrayInfo(configInfoGray);
        assertEquals(configInfoGray.getId(), configGrayInfo.getId());
        assertEquals(configInfoGray.getTenant(), configGrayInfo.getNamespaceId());
        assertEquals(configInfoGray.getGroup(), configGrayInfo.getGroupName());
        assertEquals(configInfoGray.getDataId(), configGrayInfo.getDataId());
        assertEquals(configInfoGray.getMd5(), configGrayInfo.getMd5());
        assertEquals(configInfoGray.getType(), configGrayInfo.getType());
        assertEquals(configInfoGray.getEncryptedDataKey(), configGrayInfo.getEncryptedDataKey());
        assertEquals(configInfoGray.getAppName(), configGrayInfo.getAppName());
        assertEquals(0, configGrayInfo.getCreateTime());
        assertEquals(configInfoGray.getLastModified(), configGrayInfo.getModifyTime());
        assertEquals(configInfoGray.getSrcUser(), configGrayInfo.getCreateUser());
        assertEquals(configInfoGray.getGrayName(), configGrayInfo.getGrayName());
        assertEquals(configInfoGray.getGrayRule(), configGrayInfo.getGrayRule());
    }
    
    @Test
    void testTransferToConfigHistoryBasicInfo() {
        ConfigHistoryInfo configHistoryInfo = mockConfigHistoryInfo();
        ConfigHistoryBasicInfo configHistoryBasicInfo = ResponseUtil.transferToConfigHistoryBasicInfo(
                configHistoryInfo);
        assertConfigHistoryBasicInfo(configHistoryInfo, configHistoryBasicInfo);
    }
    
    @Test
    void testTransferToConfigHistoryDetialInfo() {
        ConfigHistoryInfo configHistoryInfo = mockConfigHistoryInfo();
        ConfigHistoryDetailInfo configHistoryBasicInfo = ResponseUtil.transferToConfigHistoryDetailInfo(
                configHistoryInfo);
        assertConfigHistoryBasicInfo(configHistoryInfo, configHistoryBasicInfo);
        assertEquals(configHistoryInfo.getContent(), configHistoryBasicInfo.getContent());
        assertEquals(configHistoryInfo.getEncryptedDataKey(), configHistoryBasicInfo.getEncryptedDataKey());
        assertEquals(configHistoryInfo.getGrayName(), configHistoryBasicInfo.getGrayName());
        assertEquals(configHistoryInfo.getExtInfo(), configHistoryBasicInfo.getExtInfo());
    }
    
    private ConfigHistoryInfo mockConfigHistoryInfo() {
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setId(1L);
        configHistoryInfo.setTenant("testNs");
        configHistoryInfo.setGroup(Constants.DEFAULT_GROUP);
        configHistoryInfo.setDataId("testDs");
        configHistoryInfo.setAppName("testAppName");
        configHistoryInfo.setMd5("testMd5");
        configHistoryInfo.setContent("testContent");
        configHistoryInfo.setSrcIp("1.1.1.1");
        configHistoryInfo.setSrcUser("testSrcUser");
        configHistoryInfo.setOpType("I");
        configHistoryInfo.setPublishType("formal");
        configHistoryInfo.setGrayName("testGrayName");
        configHistoryInfo.setExtInfo("{\"type\":\"text\"}");
        configHistoryInfo.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(System.currentTimeMillis()));
        configHistoryInfo.setEncryptedDataKey("testEncryptedDataKey");
        return configHistoryInfo;
    }
    
    private void assertConfigHistoryBasicInfo(ConfigHistoryInfo configHistoryInfo,
            ConfigHistoryBasicInfo configHistoryBasicInfo) {
        assertEquals(configHistoryInfo.getId(), configHistoryBasicInfo.getId());
        assertEquals(configHistoryInfo.getTenant(), configHistoryBasicInfo.getNamespaceId());
        assertEquals(configHistoryInfo.getGroup(), configHistoryBasicInfo.getGroupName());
        assertEquals(configHistoryInfo.getDataId(), configHistoryBasicInfo.getDataId());
        assertEquals(configHistoryInfo.getAppName(), configHistoryBasicInfo.getAppName());
        assertEquals(configHistoryInfo.getMd5(), configHistoryBasicInfo.getMd5());
        assertEquals(configHistoryInfo.getSrcIp(), configHistoryBasicInfo.getSrcIp());
        assertEquals(configHistoryInfo.getSrcUser(), configHistoryBasicInfo.getSrcUser());
        assertEquals(configHistoryInfo.getOpType(), configHistoryBasicInfo.getOpType());
        assertEquals(configHistoryInfo.getPublishType(), configHistoryBasicInfo.getPublishType());
        assertEquals(configHistoryInfo.getCreatedTime().getTime(), configHistoryBasicInfo.getCreateTime());
        assertEquals(configHistoryInfo.getLastModifiedTime().getTime(), configHistoryBasicInfo.getModifyTime());
    }
}
