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
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.keran213539.commonOkHttp.CommonOkHttpClient;
import com.github.keran213539.commonOkHttp.CommonOkHttpClientBuilder;
import com.github.keran213539.commonOkHttp.UploadByteFile;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author klw
 * @date 2019/5/23 15:26
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7003"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigExportAndImportAPI_CITCase {

    private static final long TIME_OUT = 2000;
    private static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";

    private CommonOkHttpClient httpClient = new CommonOkHttpClientBuilder().build();

    @LocalServerPort
    private int port;

    private String SERVER_ADDR = null;

    private HttpAgent agent = null;
    
    @BeforeClass
    @AfterClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigExportAndImportAPI_CITCase.class.getSimpleName());
    }

    @Before
    public void setUp() throws Exception {
        SERVER_ADDR = "http://127.0.0.1"+":"+ port + "/nacos";

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();

        Map<String, String> prarm = new HashMap<>(7);
        prarm.put("dataId", "testNoAppname1.yml");
        prarm.put("group", "EXPORT_IMPORT_TEST_GROUP");
        prarm.put("content", "test: test");
        prarm.put("desc", "testNoAppname1");
        prarm.put("type", "yaml");
        Assert.assertEquals("true", httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH , prarm,null));
        prarm.put("dataId", "testNoAppname2.txt");
        prarm.put("group", "TEST1_GROUP");
        prarm.put("content", "test: test");
        prarm.put("desc", "testNoAppname2");
        prarm.put("type", "text");
        Assert.assertEquals("true", httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH , prarm,null));
        prarm.put("dataId", "testHasAppname1.properties");
        prarm.put("group", "EXPORT_IMPORT_TEST_GROUP");
        prarm.put("content", "test.test1.value=test");
        prarm.put("desc", "testHasAppname1");
        prarm.put("type", "properties");
        prarm.put("appName", "testApp1");
        Assert.assertEquals("true", httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH , prarm,null));
    }

    @After
    public void cleanup() throws Exception{
        HttpRestResult<String> result;
        try {
            Map<String, String> params = new HashMap<>();
            params.put("dataId", "testNoAppname1.yml");
            params.put("group", "EXPORT_IMPORT_TEST_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());

            params.put("dataId", "testNoAppname2.txt");
            params.put("group", "TEST1_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            params.put("dataId", "testHasAppname1.properties");
            params.put("group", "EXPORT_IMPORT_TEST_GROUP");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());

            params.put("dataId", "test1.yml");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());

            params.put("dataId", "test2.txt");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());

            params.put("dataId", "test3.properties");
            params.put("group", "TEST_IMPORT");
            params.put("beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
        } catch (Exception e) {
            Assert.fail();
        }
        agent.shutdown();
    }

    @Test()
    public void testExportByIds(){
        String getDataUrl = "?search=accurate&dataId=&group=&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        JsonNode config1 = resultConfigs.get(0);
        JsonNode config2 = resultConfigs.get(1);
        String id1 = config1.get("id").asText();
        String id2 = config2.get("id").asText();
        String exportByIdsUrl = "?export=true&tenant=&group=&appName=&ids=" + id1 + "," + id2;
        System.out.println(exportByIdsUrl);
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(2, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        String config2Name = config2.get("group").textValue() + "/" + config2.get("dataId").textValue();
        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!(config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()))){
                Assert.fail();
            }
        }
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportByGroup(){
        String getDataUrl = "?search=accurate&dataId=&group=EXPORT_IMPORT_TEST_GROUP&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        Assert.assertEquals(2, resultConfigs.size());
        JsonNode config1 = resultConfigs.get(0);
        JsonNode config2 = resultConfigs.get(1);
        String exportByIdsUrl = "?export=true&tenant=&group=EXPORT_IMPORT_TEST_GROUP&appName=&ids=";
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(2, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        String config2Name = config2.get("group").textValue() + "/" + config2.get("dataId").textValue();

        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!(config1Name.equals(zipItem.getItemName())
                || config2Name.equals(zipItem.getItemName()))){
                Assert.fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        Assert.assertNotNull(appName);
        Assert.assertEquals("testApp1", appName);
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportByGroupAndApp(){
        String getDataUrl = "?search=accurate&dataId=&group=EXPORT_IMPORT_TEST_GROUP&appName=testApp1&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        Assert.assertEquals(1, resultConfigs.size());
        JsonNode config1 = resultConfigs.get(0);
        String exportByIdsUrl = "?export=true&tenant=&group=EXPORT_IMPORT_TEST_GROUP&appName=testApp1&ids=";
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(1, zipItemList.size());
        String config1Name = config1.get("group").textValue() + "/" + config1.get("dataId").textValue();
        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!config1Name.equals(zipItem.getItemName())){
                Assert.fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        Assert.assertNotNull(appName);
        Assert.assertEquals("testApp1", appName);
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportAll(){
        String exportByIdsUrl = "?export=true&tenant=&group=&appName=&ids=";
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        String config1Name = "EXPORT_IMPORT_TEST_GROUP/testNoAppname1.yml";
        String config2Name = "TEST1_GROUP/testNoAppname2.txt";
        String config3Name = "EXPORT_IMPORT_TEST_GROUP/testHasAppname1.properties";
        int successCount = 0;
        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()) ||
            config3Name.equals(zipItem.getItemName())){
                successCount++;
            }
        }
        Assert.assertEquals(3, successCount);
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("EXPORT_IMPORT_TEST_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        Assert.assertNotNull(appName);
        Assert.assertEquals("testApp1", appName);
    }

    @Test(timeout = 3*TIME_OUT)
    public void testImport(){
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>(3);
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test1.yml", "test: test1"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test2.txt", "test: test1"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/test3.properties", "test.test1.value=test"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT_2/test4.properties", "test.test4.value=test"));
        zipItemList.add(new ZipUtils.ZipItem("TEST_IMPORT/SUB_GROUP/test5.properties", "test.test5.value=test"));
        String metaDataStr = "TEST_IMPORT.test1~yml.app=testApp1\rTEST_IMPORT.test2~txt.app=testApp2\r\nTEST_IMPORT.test3~properties.app=testApp3\nTEST_IMPORT_2.test4~properties.app=testApp4";
        zipItemList.add(new ZipUtils.ZipItem(".meta.yml", metaDataStr));
        String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        UploadByteFile uploadByteFile = new UploadByteFile();
        uploadByteFile.setFileName("testImport.zip");
        uploadByteFile.setFileBytes(ZipUtils.zip(zipItemList));
        uploadByteFile.setMediaType("application/zip");
        uploadByteFile.setPrarmName("file");
        String importResult = httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, Collections.singletonList(uploadByteFile), null);

        // test unrecognizedData
        JsonNode importResObj = JacksonUtils.toObj(importResult);
        int unrecognizedCount = importResObj.get("data").get("unrecognizedCount").intValue();
        Assert.assertEquals(1, unrecognizedCount);
        JsonNode unrecognizedData = importResObj.get("data").get("unrecognizedData").get(0);
        Assert.assertEquals("TEST_IMPORT/SUB_GROUP/test5.properties", unrecognizedData.get("itemName").textValue());

        String getDataUrl = "?search=accurate&dataId=&group=TEST_IMPORT&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JsonNode resultObj = JacksonUtils.toObj(queryResult);
        JsonNode resultConfigs = resultObj.get("pageItems");
        Assert.assertEquals(3, resultConfigs.size());
        for(int i = 0; i < resultConfigs.size(); i++){
            JsonNode config = resultConfigs.get(i);
            if(!"TEST_IMPORT".equals(config.get("group").textValue())){
                Assert.fail();
            }
            switch (config.get("dataId").textValue()){
                case "test1.yml":
                    Assert.assertEquals(config.get("appName").textValue(), "testApp1");
                    break;
                case "test2.txt":
                    Assert.assertEquals(config.get("appName").textValue(), "testApp2");
                    break;
                case "test3.properties":
                    Assert.assertEquals(config.get("appName").textValue(), "testApp3");
                    break;
                default:
                    Assert.fail();
            }
        }

        getDataUrl = "?search=accurate&dataId=&group=TEST_IMPORT_2&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        resultObj = JacksonUtils.toObj(queryResult);
        resultConfigs = resultObj.get("pageItems");
        Assert.assertEquals(1, resultConfigs.size());
        JsonNode jsonNode = resultConfigs.get(0);
        Assert.assertEquals(jsonNode.get("appName").textValue(), "testApp4");
    }

    private Map<String, String> processMetaData(ZipUtils.ZipItem metaDataZipItem){
        Map<String, String> metaDataMap = new HashMap<>(16);
        if(metaDataZipItem != null){
            String metaDataStr = metaDataZipItem.getItemData();
            String[] metaDataArr = metaDataStr.split("\r\n");
            for(String metaDataItem : metaDataArr){
                String[] metaDataItemArr = metaDataItem.split("=");
                Assert.assertEquals(2, metaDataItemArr.length);
                metaDataMap.put(metaDataItemArr[0], metaDataItemArr[1]);
            }
        }
        return metaDataMap;
    }

    private String packageMetaName(String group, String dataId){
        String tempDataId = dataId;
        if(tempDataId.contains(".")){
            tempDataId = tempDataId.substring(0, tempDataId.lastIndexOf("."))
                + "~" + tempDataId.substring(tempDataId.lastIndexOf(".") + 1);
        }
        return group + "." + tempDataId + ".app";
    }
}
