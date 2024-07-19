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

package com.alibaba.nacos.test.config;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.handler.AbstractResponseHandler;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.result.code.ResultCodeEnum;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CITCase for ConfigExportAndImportAPI.
 *
 * @author klw
 * @date 2019/5/23 15:26
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings({"checkstyle:TypeName", "checkstyle:AbbreviationAsWordInName"})
class ConfigExportAndImportAPI_CITCase {
    
    private static final long TIME_OUT = 2000;
    
    private static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";
    
    @LocalServerPort
    private int port;
    
    private String serverAddr = null;
    
    private HttpAgent agent = null;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeAll
    @AfterAll
    static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigExportAndImportAPI_CITCase.class.getSimpleName());
    }
    
    @BeforeEach
    void setUp() throws Exception {
        nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
        // register a handler to process byte[] result
        nacosRestTemplate.registerResponseHandler(byte[].class.getName(), new AbstractResponseHandler() {
            @Override
            public HttpRestResult<byte[]> convertResult(HttpClientResponse response, Type responseType) throws Exception {
                return new HttpRestResult(response.getHeaders(), response.getStatusCode(), IOUtils.toByteArray(response.getBody()), null);
            }
        });
        
        serverAddr = "http://127.0.0.1" + ":" + port + "/nacos";
        
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();
        
        Map<String, String> prarm = new HashMap<>(7);
        prarm.put("dataId", "testNoAppname1.yml");
        prarm.put("group", "EXPORT_IMPORT_TEST_GROUP");
        prarm.put("content", "test: test");
        prarm.put("desc", "testNoAppname1");
        prarm.put("type", "yaml");
        assertEquals("true", httpPost(serverAddr + CONFIG_CONTROLLER_PATH, prarm));
        prarm.put("dataId", "testNoAppname2.txt");
        prarm.put("group", "TEST1_GROUP");
        prarm.put("content", "test: test");
        prarm.put("desc", "testNoAppname2");
        prarm.put("type", "text");
        assertEquals("true", httpPost(serverAddr + CONFIG_CONTROLLER_PATH, prarm));
        prarm.put("dataId", "testHasAppname1.properties");
        prarm.put("group", "EXPORT_IMPORT_TEST_GROUP");
        prarm.put("content", "test.test1.value=test");
        prarm.put("desc", "testHasAppname1");
        prarm.put("type", "properties");
        prarm.put("appName", "testApp1");
        assertEquals("true", httpPost(serverAddr + CONFIG_CONTROLLER_PATH, prarm));
    }
    
    @AfterEach
    void cleanup() throws Exception {
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result;
            Map<String, String> params = new HashMap<>();
            params.put("dataId", "testNoAppname1.yml");
            params.put("group", "EXPORT_IMPORT_TEST_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "testNoAppname2.txt");
            params.put("group", "TEST1_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "testHasAppname1.properties");
            params.put("group", "EXPORT_IMPORT_TEST_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test1.yml");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test2.txt");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test3.properties");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test1");
            params.put("group", "TEST_IMPORT2");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test3");
            params.put("group", "TEST_IMPORT2");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "test4");
            params.put("group", "TEST_IMPORT2");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
        });
        agent.shutdown();
    }
    
    @Test
    void testExportByIds() {
        String getDataUrl = "?search=accurate&dataId=&group=&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        JsonNode config1 = resultConfigs.get(0);
        JsonNode config2 = resultConfigs.get(1);
        String id1 = config1.get("id").asText();
        String id2 = config2.get("id").asText();
        String exportByIdsUrl = "?export=true&tenant=&group=&appName=&ids=" + id1 + "," + id2;
        System.out.println(exportByIdsUrl);
        byte[] zipData = httpGetBytes(serverAddr + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        assertEquals(2, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        String config2Name = config2.get("group").textValue() + "/" + config2.get("dataId").textValue();
        for (ZipUtils.ZipItem zipItem : zipItemList) {
            if (!(config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()))) {
                fail();
            }
        }
    }
    
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void testExportByGroup() {
        String getDataUrl = "?search=accurate&dataId=&group=EXPORT_IMPORT_TEST_GROUP&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        assertEquals(2, resultConfigs.size());
        JsonNode config1 = resultConfigs.get(0);
        JsonNode config2 = resultConfigs.get(1);
        String exportByIdsUrl = "?export=true&tenant=&group=EXPORT_IMPORT_TEST_GROUP&appName=&ids=";
        byte[] zipData = httpGetBytes(serverAddr + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        assertEquals(2, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        String config2Name = config2.get("group").textValue() + "/" + config2.get("dataId").textValue();
        
        for (ZipUtils.ZipItem zipItem : zipItemList) {
            if (!(config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()))) {
                fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        assertNotNull(appName);
        assertEquals("testApp1", appName);
    }
    
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void testExportByGroupAndApp() {
        String getDataUrl = "?search=accurate&dataId=&group=EXPORT_IMPORT_TEST_GROUP&appName=testApp1&config_tags="
                + "&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        assertEquals(1, resultConfigs.size());
        JsonNode config1 = resultConfigs.get(0);
        String exportByIdsUrl = "?export=true&tenant=&group=EXPORT_IMPORT_TEST_GROUP&appName=testApp1&ids=";
        byte[] zipData = httpGetBytes(serverAddr + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        assertEquals(1, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        for (ZipUtils.ZipItem zipItem : zipItemList) {
            if (!config1Name.equals(zipItem.getItemName())) {
                fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        assertNotNull(appName);
        assertEquals("testApp1", appName);
    }
    
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void testExportAll() {
        String exportByIdsUrl = "?export=true&tenant=&group=&appName=&ids=";
        byte[] zipData = httpGetBytes(serverAddr + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        String config1Name = "EXPORT_IMPORT_TEST_GROUP/testNoAppname1.yml";
        String config2Name = "TEST1_GROUP/testNoAppname2.txt";
        String config3Name = "EXPORT_IMPORT_TEST_GROUP/testHasAppname1.properties";
        int successCount = 0;
        for (ZipUtils.ZipItem zipItem : zipItemList) {
            if (config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()) || config3Name.equals(
                    zipItem.getItemName())) {
                successCount++;
            }
        }
        assertEquals(3, successCount);
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        assertNotNull(appName);
        assertEquals("testApp1", appName);
    }
    
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    void testImport() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test1.yml", "test: test1"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test2.txt", "test: test1"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test3.properties", "test.test1.value=test"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT_2/test4.properties", "test.test4.value=test"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/SUB_GROUP/test5.properties", "test.test5.value=test"));
        String metaDataStr = "TEST_IMPORT.test1~yml.app=testApp1\rTEST_IMPORT.test2~txt.app=testApp2\r\n"
                + "TEST_IMPORT.test3~properties.app=testApp3\nTEST_IMPORT_2.test4~properties.app=testApp4";
        zipItemList.add(new ZipUtils.ZipItem(".meta.yml", metaDataStr));
        final String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        String importResult = uploadZipFile(serverAddr + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, "testImport.zip",
                ZipUtils.zip(zipItemList));
        System.out.println("importResult: " + importResult);
        
        // test unrecognizedData
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        int unrecognizedCount = importResObj.get("data").get("unrecognizedCount").intValue();
        assertEquals(1, unrecognizedCount);
        JsonNode unrecognizedData = importResObj.get("data").get("unrecognizedData").get(0);
        assertEquals("TEST_IMPORT/SUB_GROUP/test5.properties", unrecognizedData.get("itemName").textValue());
        
        String getDataUrl = "?search=accurate&dataId=&group=TEST_IMPORT&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        assertEquals(3, resultConfigs.size());
        for (int i = 0; i < resultConfigs.size(); i++) {
            JsonNode config = resultConfigs.get(i);
            if (!"TEST_IMPORT".equals(config.get("group").textValue())) {
                fail();
            }
            switch (config.get("dataId").textValue()) {
                case "test1.yml":
                    assertEquals("testApp1", config.get("appName").textValue());
                    break;
                case "test2.txt":
                    assertEquals("testApp2", config.get("appName").textValue());
                    break;
                case "test3.properties":
                    assertEquals("testApp3", config.get("appName").textValue());
                    break;
                default:
                    fail();
            }
        }
        
        getDataUrl = "?search=accurate&dataId=&group=TEST_IMPORT_2&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        resultObj = JacksonUtils.toObj(queryResult);
        resultConfigs = resultObj.get("pageItems");
        assertEquals(1, resultConfigs.size());
        JsonNode jsonNode = resultConfigs.get(0);
        assertEquals("testApp4", jsonNode.get("appName").textValue());
    }
    
    private Map<String, String> processMetaData(ZipUtils.ZipItem metaDataZipItem) {
        Map<String, String> metaDataMap = new HashMap<>(16);
        if (metaDataZipItem != null) {
            String metaDataStr = metaDataZipItem.getItemData();
            String[] metaDataArr = metaDataStr.split("\r\n");
            for (String metaDataItem : metaDataArr) {
                String[] metaDataItemArr = metaDataItem.split("=");
                assertEquals(2, metaDataItemArr.length);
                metaDataMap.put(metaDataItemArr[0], metaDataItemArr[1]);
            }
        }
        return metaDataMap;
    }
    
    private String packageMetaName(String group, String dataId) {
        String tempDataId = dataId;
        if (tempDataId.contains(".")) {
            tempDataId = tempDataId.substring(0, tempDataId.lastIndexOf(".")) + "~" + tempDataId.substring(tempDataId.lastIndexOf(".") + 1);
        }
        return group + "." + tempDataId + ".app";
    }
    
    @Test
    void testExportV2() {
        String dataId = "testNoAppname2.txt";
        String getDataUrl = "?search=accurate&group=TEST1_GROUP&pageNo=1&pageSize=10&tenant=&namespaceId=&dataId=" + dataId;
        String queryResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        JsonNode config1 = resultConfigs.get(0);
        String configId = config1.get("id").asText();
        String exportByIdsUrl = "?exportV2=true&tenant=&group=&appName=&ids=" + configId;
        byte[] zipData = httpGetBytes(serverAddr + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        assertEquals(1, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        
        for (ZipUtils.ZipItem zipItem : zipItemList) {
            if (!(config1Name.equals(zipItem.getItemName()))) {
                fail();
            }
        }
        assertEquals(dataId, config1.get("dataId").asText());
        String group = config1.get("group").asText();
        
        String queryConfigDetailResult = httpGetString(
                serverAddr + CONFIG_CONTROLLER_PATH + "?show=all&dataId=" + dataId + "&group=" + group, null);
        JsonNode configDetailResult = JacksonUtils.toObj(queryConfigDetailResult);
        assertNotNull(configDetailResult);
        // verification metadata
        ZipUtils.ZipItem metaDataItem = unZiped.getMetaDataItem();
        assertNotNull(metaDataItem);
        String metaDataItemItemData = metaDataItem.getItemData();
        ConfigMetadata configMetadata = YamlParserUtil.loadObject(metaDataItemItemData, ConfigMetadata.class);
        assertNotNull(configMetadata);
        assertEquals(1, configMetadata.getMetadata().size());
        
        ConfigMetadata.ConfigExportItem config1Metadata = new ConfigMetadata.ConfigExportItem();
        config1Metadata.setDataId(dataId);
        config1Metadata.setGroup(group);
        config1Metadata.setType(configDetailResult.get("type").asText());
        config1Metadata.setAppName(configDetailResult.get("appName") == null ? null : configDetailResult.get("appName").asText());
        config1Metadata.setDesc(configDetailResult.get("desc") == null ? null : configDetailResult.get("desc").asText());
        
        ConfigMetadata.ConfigExportItem configExportItem1 = configMetadata.getMetadata().get(0);
        assertEquals(configExportItem1, config1Metadata);
    }
    
    @Test
    void testImportV2() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT2/test1", "test: test1"));
        String metaDataStr =
                "metadata:\n" + "- appName: testAppName\n" + "  dataId: test1\n" + "  desc: testDesc\n" + "  group: TEST_IMPORT2\n"
                        + "  type: yaml";
        
        zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, metaDataStr));
        final String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        String importResult = uploadZipFile(serverAddr + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, "testImport.zip",
                ZipUtils.zip(zipItemList));
        
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        assertEquals(1, importResObj.get("data").get("succCount").asInt());
        
        String queryConfigDetailResult = httpGetString(serverAddr + CONFIG_CONTROLLER_PATH + "?show=all&dataId=test1&group=TEST_IMPORT2",
                null);
        JsonNode configDetailResult = JacksonUtils.toObj(queryConfigDetailResult);
        assertNotNull(configDetailResult);
        
        assertEquals("test1", configDetailResult.get("dataId").asText());
        assertEquals("TEST_IMPORT2", configDetailResult.get("group").asText());
        assertEquals("yaml", configDetailResult.get("type").asText());
        assertEquals("testAppName", configDetailResult.get("appName").asText());
        assertEquals("testDesc", configDetailResult.get("desc").asText());
    }
    
    @Test
    void testImportV2MetadataError() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT2/test2", "test: test2"));
        String metaDataStr = "metadata:\n" + "- appName: testAppName\n" + "  desc: test desc\n" + "  group: TEST_IMPORT\n" + "  type: yaml";
        
        zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, metaDataStr));
        final String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        String importResult = uploadZipFile(serverAddr + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, "testImport.zip",
                ZipUtils.zip(zipItemList));
        
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        assertEquals(importResObj.get("code").intValue(), ResultCodeEnum.METADATA_ILLEGAL.getCode());
        assertEquals(importResObj.get("message").textValue(), ResultCodeEnum.METADATA_ILLEGAL.getCodeMsg());
    }
    
    @Test
    void testImportV2MetadataNotFind() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT2/test3.yml", "test: test3"));
        String metaDataStr = "metadata:\n" + "- dataId: notExist\n" + "  group: TEST_IMPORT2\n" + "  type: yaml\n" + "- dataId: test3.yml\n"
                + "  group: TEST_IMPORT2\n" + "  type: yaml";
        
        zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, metaDataStr));
        final String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        String importResult = uploadZipFile(serverAddr + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, "testImport.zip",
                ZipUtils.zip(zipItemList));
        
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        JsonNode data = importResObj.get("data");
        assertEquals(1, data.get("succCount").intValue());
        // test unrecognizedData
        int unrecognizedCount = data.get("unrecognizedCount").intValue();
        assertEquals(1, unrecognizedCount);
        JsonNode unrecognizedData = data.get("unrecognizedData").get(0);
        assertEquals("未在文件中找到: TEST_IMPORT2/notExist", unrecognizedData.get("itemName").textValue());
        
    }
    
    @Test
    void testImportV2ConfigIgnore() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT2/test4", "test: test4"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT2/ignore.yml", "test: test4"));
        String metaDataStr =
                "metadata:\n" + "- appName: testAppName\n" + "  dataId: test4\n" + "  desc: testDesc\n" + "  group: TEST_IMPORT2\n"
                        + "  type: yaml";
        
        zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, metaDataStr));
        final String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        String importResult = uploadZipFile(serverAddr + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, "testImport.zip",
                ZipUtils.zip(zipItemList));
        
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        JsonNode data = importResObj.get("data");
        assertEquals(1, data.get("succCount").intValue());
        // test unrecognizedData
        int unrecognizedCount = data.get("unrecognizedCount").intValue();
        assertEquals(1, unrecognizedCount);
        JsonNode unrecognizedData = data.get("unrecognizedData").get(0);
        assertEquals("未在元数据中找到: TEST_IMPORT2/ignore.yml", unrecognizedData.get("itemName").textValue());
    }
    
    private String httpGetString(String url, Map<String, String> param) {
        Query query = Query.newInstance().initParams(param);
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
            return httpResult.getData();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }
    
    private byte[] httpGetBytes(String url, Map<String, String> param) {
        Query query = Query.newInstance().initParams(param);
        try {
            HttpRestResult<byte[]> httpResult = nacosRestTemplate.get(url, Header.EMPTY, query, byte[].class);
            return httpResult.getData();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }
    
    private String httpPost(String url, Map<String, String> param) {
        return httpPost(url, param, null);
    }
    
    private String httpPost(String url, Map<String, String> param, Object payload) {
        Query query = Query.newInstance().initParams(param);
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.post(url, Header.EMPTY, query, payload, String.class);
            return httpResult.getData();
        } catch (Exception e) {
            throw new RuntimeException("", e);
        }
    }
    
    private String uploadZipFile(String url, Map<String, String> param, String filename, byte[] fileBytes) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            int connectTimeout = 10000;
            int socketTimeout = 10000;
            HttpPost httpPost = new HttpPost(url);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(socketTimeout).build();
            httpPost.setConfig(requestConfig);
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            if (MapUtils.isNotEmpty(param)) {
                param.entrySet().forEach(e -> {
                    builder.addTextBody(e.getKey(), e.getValue(), ContentType.APPLICATION_FORM_URLENCODED);
                });
            }
            
            ByteArrayBody byteArrayBody = new ByteArrayBody(fileBytes, ContentType.create("application/zip"), filename);
            builder.addPart("file", byteArrayBody);
            
            HttpEntity reqEntity = builder.setContentType(ContentType.MULTIPART_FORM_DATA).build();
            httpPost.setEntity(reqEntity);
            
            CloseableHttpResponse response = httpclient.execute(httpPost);
            try {
                System.out.println(response.getStatusLine());
                HttpEntity resEntity = response.getEntity();
                String responseToStr = null;
                if (resEntity != null) {
                    responseToStr = EntityUtils.toString(response.getEntity());
                }
                EntityUtils.consume(resEntity);
                return responseToStr;
            } finally {
                response.close();
            }
        } catch (Throwable e) {
            throw new RuntimeException("httpPostZipFile error", e);
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
