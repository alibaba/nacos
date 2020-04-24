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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient;
import com.alibaba.nacos.config.server.Config;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.github.keran213539.commonOkHttp.CommonOkHttpClient;
import com.github.keran213539.commonOkHttp.CommonOkHttpClientBuilder;
import com.github.keran213539.commonOkHttp.UploadByteFile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author klw
 * @date 2019/5/23 15:26
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigExportAndImportAPI_ITCase {
    private static final long TIME_OUT = 2000;
    private static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";

    private CommonOkHttpClient httpClient = new CommonOkHttpClientBuilder().build();

    @LocalServerPort
    private int port;

    private String SERVER_ADDR = null;

    private HttpAgent agent = null;


    @Before
    public void setUp() throws Exception {
        SERVER_ADDR = "http://127.0.0.1"+":"+ port + "/nacos";

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();

        Map<String, String> prarm = new HashMap<>(7);
        prarm.put("dataId", "testNoAppname1.yml");
        prarm.put("group", "DEFAULT_GROUP");
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
        prarm.put("group", "DEFAULT_GROUP");
        prarm.put("content", "test.test1.value=test");
        prarm.put("desc", "testHasAppname1");
        prarm.put("type", "properties");
        prarm.put("appName", "testApp1");
        Assert.assertEquals("true", httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH , prarm,null));
    }

    @After
    public void cleanup(){
        HttpSimpleClient.HttpResult result;
        try {
            List<String> params2 = Arrays.asList("dataId", "testNoAppname1.yml", "group", "DEFAULT_GROUP", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params2, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            List<String> params3 = Arrays.asList("dataId", "testNoAppname2.txt", "group", "TEST1_GROUP", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params3, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            List<String> params4 = Arrays.asList("dataId", "testHasAppname1.properties", "group", "DEFAULT_GROUP", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params4, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            List<String> params5 = Arrays.asList("dataId", "test1.yml", "group", "TEST_IMPORT", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params4, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            List<String> params6 = Arrays.asList("dataId", "test2.txt", "group", "TEST_IMPORT", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params4, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            List<String> params7 = Arrays.asList("dataId", "test3.properties", "group", "TEST_IMPORT", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params4, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportByIds(){
        String getDataUrl = "?search=accurate&dataId=&group=&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JSONObject resultObj = JSON.parseObject(queryResult);
        JSONArray resultConfigs = resultObj.getJSONArray("pageItems");
        JSONObject config1 = resultConfigs.getJSONObject(0);
        JSONObject config2 = resultConfigs.getJSONObject(1);
        String exportByIdsUrl = "?export=true&tenant=&group=&appName=&ids=" + config1.getIntValue("id")
            + "," + config2.getIntValue("id");
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(2, zipItemList.size());
        String config1Name = config1.getString("group") + "/" + config1.getString("dataId");
        String config2Name = config2.getString("group") + "/" + config2.getString("dataId");
        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!(config1Name.equals(zipItem.getItemName()) || config2Name.equals(zipItem.getItemName()))){
                Assert.fail();
            }
        }
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportByGroup(){
        String getDataUrl = "?search=accurate&dataId=&group=DEFAULT_GROUP&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JSONObject resultObj = JSON.parseObject(queryResult);
        JSONArray resultConfigs = resultObj.getJSONArray("pageItems");
        Assert.assertEquals(3, resultConfigs.size());
        JSONObject config1 = resultConfigs.getJSONObject(0);
        JSONObject config2 = resultConfigs.getJSONObject(1);
        JSONObject config3 = resultConfigs.getJSONObject(2);
        String exportByIdsUrl = "?export=true&tenant=&group=DEFAULT_GROUP&appName=&ids=";
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(3, zipItemList.size());
        String config1Name = config1.getString("group") + "/" + config1.getString("dataId");
        String config2Name = config2.getString("group") + "/" + config2.getString("dataId");
        String config3Name = config3.getString("group") + "/" + config3.getString("dataId");

        System.out.println(config1Name + ", " + config2Name + ", " + config3Name);

        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!(config1Name.equals(zipItem.getItemName())
                || config2Name.equals(zipItem.getItemName())
                || config3Name.equals(zipItem.getItemName()))){
                Assert.fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("DEFAULT_GROUP", "testHasAppname1.properties");
        String appName = metaData.get(metaDataName);
        Assert.assertNotNull(appName);
        Assert.assertEquals("testApp1", appName);
    }

    @Test(timeout = 3*TIME_OUT)
    public void testExportByGroupAndApp(){
        String getDataUrl = "?search=accurate&dataId=&group=DEFAULT_GROUP&appName=testApp1&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JSONObject resultObj = JSON.parseObject(queryResult);
        JSONArray resultConfigs = resultObj.getJSONArray("pageItems");
        Assert.assertEquals(1, resultConfigs.size());
        JSONObject config1 = resultConfigs.getJSONObject(0);
        String exportByIdsUrl = "?export=true&tenant=&group=DEFAULT_GROUP&appName=testApp1&ids=";
        byte[] zipData = httpClient.download(SERVER_ADDR + CONFIG_CONTROLLER_PATH + exportByIdsUrl, null);
        ZipUtils.UnZipResult unZiped = ZipUtils.unzip(zipData);
        List<ZipUtils.ZipItem> zipItemList = unZiped.getZipItemList();
        Assert.assertEquals(1, zipItemList.size());
        String config1Name = config1.getString("group") + "/" + config1.getString("dataId");
        for(ZipUtils.ZipItem zipItem : zipItemList){
            if(!config1Name.equals(zipItem.getItemName())){
                Assert.fail();
            }
        }
        // verification metadata
        Map<String, String> metaData = processMetaData(unZiped.getMetaDataItem());
        String metaDataName = packageMetaName("DEFAULT_GROUP", "testHasAppname1.properties");
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
        String config1Name = "DEFAULT_GROUP/testNoAppname1.yml";
        String config2Name = "TEST1_GROUP/testNoAppname2.txt";
        String config3Name = "DEFAULT_GROUP/testHasAppname1.properties";
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
        String metaDataName = packageMetaName("DEFAULT_GROUP", "testHasAppname1.properties");
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
        String metaDataStr = "TEST_IMPORT.test2~txt.app=testApp1\r\nTEST_IMPORT.test3~properties.app=testApp2";
        zipItemList.add(new ZipUtils.ZipItem(".meta.yml", metaDataStr));
        String importUrl = "?import=true&namespace=";
        Map<String, String> importPrarm = new HashMap<>(1);
        importPrarm.put("policy", "OVERWRITE");
        UploadByteFile uploadByteFile = new UploadByteFile();
        uploadByteFile.setFileName("testImport.zip");
        uploadByteFile.setFileBytes(ZipUtils.zip(zipItemList));
        uploadByteFile.setMediaType("application/zip");
        uploadByteFile.setPrarmName("file");
        httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH + importUrl, importPrarm, Collections.singletonList(uploadByteFile), null);
        String getDataUrl = "?search=accurate&dataId=&group=TEST_IMPORT&appName=&config_tags=&pageNo=1&pageSize=10&tenant=&namespaceId=";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_CONTROLLER_PATH + getDataUrl, null);
        JSONObject resultObj = JSON.parseObject(queryResult);
        JSONArray resultConfigs = resultObj.getJSONArray("pageItems");
        Assert.assertEquals(3, resultConfigs.size());
        for(int i = 0; i < resultConfigs.size(); i++){
            JSONObject config = resultConfigs.getJSONObject(i);
            if(!"TEST_IMPORT".equals(config.getString("group"))){
                Assert.fail();
            }
            switch (config.getString("dataId")){
                case "test1.yml":
                case "test2.txt":
                case "test3.properties":
                    break;
                default:
                    Assert.fail();
            }
        }
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
