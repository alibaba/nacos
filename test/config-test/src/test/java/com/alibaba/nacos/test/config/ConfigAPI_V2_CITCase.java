/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.Random;

import static com.alibaba.nacos.test.config.ConfigAPI_V2_CITCase.CONTEXT_PATH;

/**
 * @author karsonto
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=" + CONTEXT_PATH}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@FixMethodOrder(MethodSorters.JVM)
public class ConfigAPI_V2_CITCase extends HttpClient4Test {
    
    public static final long TIME_OUT = 5000;
    
    public static final String CONTEXT_PATH = "/nacos";
    
    private static final String CONFIG_V2_CONTROLLER_PATH = CONTEXT_PATH + Constants.CONFIG_CONTROLLER_V2_PATH;
    
    private static final String CONTENT = randomContent();
    
    private final String DATA_ID = "nacos.example";
    
    private final String GROUP = "DEFAULT_GROUP";
    
    private final String NAME_SPACE_ID = "public";
    
    @LocalServerPort
    private int port;
    
    @BeforeClass
    public static void beforeClass() {
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigAPI_V2_CITCase.class.getSimpleName());
    }
    
    
    @AfterClass
    @BeforeClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }
    
    @Before
    public void setUp() throws Exception {
        String url = String.format("http://127.0.0.1:%d/", port);
        this.base = new URL(url);
    }
    
    @Test
    public void test() throws Exception {
        publishConfig();
        Thread.sleep(TIME_OUT);
        String config = getConfig(true);
        Assert.assertEquals(config, CONTENT);
        Thread.sleep(TIME_OUT);
        deleteConfig();
        Thread.sleep(TIME_OUT);
        boolean thrown = false;
        try {
            getConfig(false);
        } catch (Exception e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
    }
    
    
    public void publishConfig() throws Exception {
        ResponseEntity<String> response = request(CONFIG_V2_CONTROLLER_PATH,
                Params.newParams().appendParam("dataId", DATA_ID).appendParam("group", GROUP)
                        .appendParam("namespaceId", NAME_SPACE_ID).appendParam("content", CONTENT).done(), String.class,
                HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        Assert.assertEquals(true, json.get("data").asBoolean());
        
    }
    
    public String getConfig(boolean ignoreStatusCode) throws Exception {
        ResponseEntity<String> response = request(CONFIG_V2_CONTROLLER_PATH,
                Params.newParams().appendParam("dataId", DATA_ID).appendParam("group", GROUP)
                        .appendParam("namespaceId", NAME_SPACE_ID).done(), String.class, HttpMethod.GET);
        if (!ignoreStatusCode) {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Fail to get config");
            }
        } else {
            Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        }
        JsonNode json = JacksonUtils.toObj(response.getBody());
        return json.get("data").asText();
    }
    
    public void deleteConfig() throws Exception {
        ResponseEntity<String> response = request(CONFIG_V2_CONTROLLER_PATH,
                Params.newParams().appendParam("dataId", DATA_ID).appendParam("group", GROUP)
                        .appendParam("namespaceId", NAME_SPACE_ID).done(), String.class, HttpMethod.DELETE);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        Assert.assertEquals(true, json.get("data").asBoolean());
        
    }
    
    
    public static String randomContent() {
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();
        int temp = rand.nextInt(10) + 1;
        sb.append("contentTest");
        for (int i = 0; i < temp; i++) {
            sb.append(i);
        }
        return sb.toString();
    }
}
