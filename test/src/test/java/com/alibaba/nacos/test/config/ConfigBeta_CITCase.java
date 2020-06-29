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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.test.base.Params;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author xiaochun.xxc
 * @date 2019-07-03
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7002"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigBeta_CITCase {

    @LocalServerPort
    private int port;

    private String url;

    @Autowired
    private TestRestTemplate restTemplate;

    static final String CONFIG_CONTROLLER_PATH = "/nacos/v1/cs";

    String dataId = "com.dungu.test";
    String group = "default";
    String tenant = "dungu";
    String content = "test";
    String appName = "nacos";
    
    @BeforeClass
    @AfterClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }

    @Before
    public void init() throws NacosException {
        url = String.format("http://localhost:%d", port);
    }

    /**
     * @TCDescription : 正常发布Beta配置
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void publishBetaConfig() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("betaIps", "127.0.0.1,127.0.0.2");

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("publishBetaConfig : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());
    }

    /**
     * @TCDescription : 必选content未设置的发布Beta配置
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void publishBetaConfig_no_content() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("betaIps", "127.0.0.1,127.0.0.2");

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("publishBetaConfig_no_content : " + response);
        Assert.assertFalse(response.getStatusCode().is2xxSuccessful());
    }

    /**
     * @TCDescription : 可选参数betaIps不存在时，发布Beta配置应该不成功。
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void publishBetaConfig_noBetaIps_beta() throws Exception {
        HttpHeaders headers = new HttpHeaders(); //不存在betaIps

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("publishBetaConfig_noBetaIps_beta post : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        ResponseEntity<String> response1 = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("publishBetaConfig_noBetaIps_beta get : " + response);
        Assert.assertTrue(response1.getStatusCode().is2xxSuccessful());
        Assert.assertTrue(JacksonUtils.toObj(response1.getBody()).get("data").isNull());
    }

    /**
     * @TCDescription : 可选参数betaIps不存在时，发布Beta配置应该不成功。
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void publishBetaConfig_noBetaIps() throws Exception {

        HttpHeaders headers = new HttpHeaders(); //不存在betaIps

        final String dataId = "publishBetaConfig_noBetaIps";
        final String groupId = "publishBetaConfig_noBetaIps";
        final String content = "publishBetaConfig_noBetaIps";

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", groupId)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("publishBetaConfig_noBetaIps post : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        ThreadUtils.sleep(10_000L);

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=false",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", groupId)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("publishBetaConfig_noBetaIps get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals(content, response.getBody());
    }

    /**
     * @TCDescription : 正常获取Beta配置
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void getBetaConfig() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("betaIps", "127.0.0.1,127.0.0.2");

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("getBetaConfig post : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("getBetaConfig get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("com.dungu.test", JacksonUtils.toObj(response.getBody()).get("data").get("dataId").asText());
    }

    /**
     * @TCDescription : 正常删除Beta配置
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void deleteBetaConfig() throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.add("betaIps", "127.0.0.1,127.0.0.2");

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("deleteBetaConfig post : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("deleteBetaConfig get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("com.dungu.test", JacksonUtils.toObj(response.getBody()).get("data").get("dataId").asText());
    
        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.DELETE);
        System.out.println("deleteBetaConfig delete : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", JacksonUtils.toObj(response.getBody()).get("data").asText());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("deleteBetaConfig after delete then get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertTrue(JacksonUtils.toObj(response.getBody()).get("data").isNull());
    }


    /**
     * @TCDescription : beta=false时，删除Beta配置
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void deleteBetaConfig_delete_beta_false() throws Exception {

        HttpHeaders headers = new HttpHeaders();
        headers.add("betaIps", "127.0.0.1,127.0.0.2");

        ResponseEntity<String> response = request(CONFIG_CONTROLLER_PATH + "/configs", headers,
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .appendParam("content", content)
                .appendParam("config_tags", "")
                .appendParam("appName", appName)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println("deleteBetaConfig_delete_beta_false post : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("deleteBetaConfig_delete_beta_false get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("com.dungu.test", JacksonUtils.toObj(response.getBody()).get("data").get("dataId").asText());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=false",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.DELETE);
        System.out.println("deleteBetaConfig_delete_beta_false delete : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("true", response.getBody());

        response = request(CONFIG_CONTROLLER_PATH + "/configs?beta=true",
            Params.newParams()
                .appendParam("dataId", dataId)
                .appendParam("group", group)
                .appendParam("tenant", tenant)
                .done(),
            String.class,
            HttpMethod.GET);
        System.out.println("deleteBetaConfig_delete_beta_false after delete then get : " + response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("com.dungu.test", JacksonUtils.toObj(response.getBody()).get("data").get("dataId").asText());
    }

    <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz, HttpMethod httpMethod) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.url.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), httpMethod, entity, clazz);
    }

    <T> ResponseEntity<T> request(String path, HttpHeaders headers, MultiValueMap<String, String> params, Class<T> clazz, HttpMethod httpMethod) {

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.url.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), httpMethod, entity, clazz);
    }

}
