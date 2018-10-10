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
package com.alibaba.nacos.test.naming;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

import java.net.URL;

import static org.junit.Assert.assertTrue;

/**
 * @author dungu.zpf
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos",
        "server.port=7001"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestAPI_ITCase {

    @LocalServerPort
    private int port;

    private URL base;

    @Autowired
    private TestRestTemplate restTemplate;

    @Before
    public void setUp() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
        prepareData();
    }

    @After
    public void cleanup() throws Exception {
        removeData();
    }

    @Test
    public void dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("name"));
    }

    @Test
    public void domCount() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/domCount",
                Params.newParams().done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

    }

    @Test
    public void rt4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/rt4Dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

    }

    @Test
    public void ip4Dom2() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/ip4Dom2",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertNotNull(json.getJSONArray("ips"));
        Assert.assertEquals(1, json.getJSONArray("ips").size());
        Assert.assertEquals(NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT_4_DOM_1,
                json.getJSONArray("ips").getString(0).split("_")[0]);

    }

    @Test
    public void ip4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/ip4Dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertNotNull(json.getJSONArray("ips"));
        Assert.assertEquals(1, json.getJSONArray("ips").size());
        Assert.assertEquals(NamingBase.TEST_IP_4_DOM_1, json.getJSONArray("ips").getJSONObject(0).getString("ip"));
        Assert.assertEquals(NamingBase.TEST_PORT_4_DOM_1, json.getJSONArray("ips").getJSONObject(0).getString("port"));

    }

    @Test
    public void replaceDom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/replaceDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1)
                        .appendParam("protectThreshold", "0.5")
                        .appendParam("enableHealthCheck", "false")
                        .appendParam("cktype", "HTTP")
                        .appendParam("ipPort4Check", "false")
                        .appendParam("path", "/hello")
                        .appendParam("headers", "1.1.1.1")
                        .appendParam("defCkport", "8080")
                        .appendParam("defIPPort", "8888")
                        .done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/naming/api/dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("name"));
        Assert.assertEquals("0.5", json.getString("protectThreshold"));
        Assert.assertEquals(NamingBase.TEST_TOKEN_4_DOM_1, json.getString("token"));
        Assert.assertEquals("false", json.getString("enableHealthCheck"));

        JSONArray clusters = json.getJSONArray("clusters");
        Assert.assertNotNull(clusters);
        Assert.assertEquals(1, clusters.size());
        Assert.assertEquals(false, clusters.getJSONObject(0).getBooleanValue("useIPPort4Check"));
        Assert.assertEquals(8888, clusters.getJSONObject(0).getIntValue("defIPPort"));
        Assert.assertEquals(8080, clusters.getJSONObject(0).getIntValue("defCkport"));

    }

    @Test
    public void regAndDeregService() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/regService",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_2)
                        .appendParam("app", "test1")
                        .appendParam("ip", NamingBase.TEST_IP_4_DOM_2)
                        .appendParam("port", NamingBase.TEST_PORT_4_DOM_2)
                        .appendParam("cluster", "DEFAULT")
                        .appendParam("token", NamingBase.TETS_TOKEN_4_DOM_2)
                        .done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/deRegService",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_2)
                        .appendParam("ip", NamingBase.TEST_IP_4_DOM_2)
                        .appendParam("port", NamingBase.TEST_PORT_4_DOM_2)
                        .appendParam("cluster", "DEFAULT")
                        .appendParam("token", NamingBase.TETS_TOKEN_4_DOM_2)
                        .done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void updateDom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/updateDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1)
                        .appendParam("protectThreshold", "0.8")
                        .appendParam("enableHealthCheck", "false")
                        .appendParam("cktype", "TCP")
                        .appendParam("ipPort4Check", "false")
                        .appendParam("defCkPort", "10000")
                        .appendParam("defIPPort", "20000")
                        .done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("name"));
        Assert.assertEquals("0.8", json.getString("protectThreshold"));
        Assert.assertEquals("false", json.getString("enableHealthCheck"));

        JSONArray clusters = json.getJSONArray("clusters");
        Assert.assertNotNull(clusters);
        Assert.assertEquals(1, clusters.size());
        Assert.assertEquals(false, clusters.getJSONObject(0).getBooleanValue("useIPPort4Check"));
        Assert.assertEquals(20000, clusters.getJSONObject(0).getIntValue("defIPPort"));
        Assert.assertEquals(10000, clusters.getJSONObject(0).getIntValue("defCkport"));

    }

    @Test
    public void hello() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/hello",
                Params.newParams().done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void replaceIP4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/replaceIP4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("cluster", "DEFAULT")
                        .appendParam("ipList", NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT2_4_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1)
                        .done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/ip4Dom2",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertNotNull(json.getJSONArray("ips"));
        Assert.assertEquals(1, json.getJSONArray("ips").size());
        Assert.assertEquals(NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT2_4_DOM_1,
                json.getJSONArray("ips").getString(0).split("_")[0]);

    }

    @Test
    public void srvAllIP() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/srvAllIP",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("dom"));
        JSONArray hosts = json.getJSONArray("hosts");
        Assert.assertNotNull(hosts);
        Assert.assertEquals(1, hosts.size());
        Assert.assertEquals(NamingBase.TEST_IP_4_DOM_1, hosts.getJSONObject(0).getString("ip"));
        Assert.assertEquals(NamingBase.TEST_PORT_4_DOM_1, hosts.getJSONObject(0).getString("port"));
    }

    @Test
    public void srvIPXT() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/srvIPXT",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(), String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("dom"));
        JSONArray hosts = json.getJSONArray("hosts");
        Assert.assertNotNull(hosts);
        Assert.assertEquals(1, hosts.size());
        Assert.assertEquals(NamingBase.TEST_IP_4_DOM_1, hosts.getJSONObject(0).getString("ip"));
        Assert.assertEquals(NamingBase.TEST_PORT_4_DOM_1, hosts.getJSONObject(0).getString("port"));
    }

    @Test
    public void remvIP4Dom() throws Exception {


        ResponseEntity<String> response = request("/nacos/v1/ns/api/addIP4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("ipList", NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT2_4_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1).done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/remvIP4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("ipList", NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT2_4_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1).done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void updateSwitch() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "distroThreshold")
                        .appendParam("distroThreshold", "0.3")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "enableAllDomNameCache")
                        .appendParam("enableAllDomNameCache", "false")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "incrementalList")
                        .appendParam("incrementalList", "1.com,2.com")
                        .appendParam("action", "update")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "healthCheckWhiteList")
                        .appendParam("healthCheckWhiteList", "1.com,2.com")
                        .appendParam("action", "update")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "clientBeatInterval")
                        .appendParam("clientBeatInterval", "5000")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "pushVersion")
                        .appendParam("type", "java")
                        .appendParam("version", "4.0.0")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "pushCacheMillis")
                        .appendParam("millis", "30000")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/updateSwitch",
                Params.newParams()
                        .appendParam("entry", "defaultCacheMillis")
                        .appendParam("millis", "3000")
                        .appendParam("token", "xy").done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/switches",
                Params.newParams().done(),
                String.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject switches = JSON.parseObject(response.getBody());

        System.out.println(switches);

        Assert.assertEquals("0.3", switches.getString("distroThreshold"));
        Assert.assertEquals("false", switches.getString("allDomNameCache"));
        Assert.assertTrue(switches.getJSONArray("incrementalList").contains("1.com"));
        Assert.assertTrue(switches.getJSONArray("incrementalList").contains("2.com"));
        Assert.assertTrue(switches.getJSONArray("healthCheckWhiteList").contains("1.com"));
        Assert.assertTrue(switches.getJSONArray("healthCheckWhiteList").contains("2.com"));
        Assert.assertEquals("5000", switches.getString("clientBeatInterval"));
        Assert.assertEquals("4.0.0", switches.getString("pushJavaVersion"));
        Assert.assertEquals("30000", switches.getString("defaultPushCacheMillis"));
        Assert.assertEquals("3000", switches.getString("defaultCacheMillis"));
    }

    @Test
    public void checkStatus() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/checkStatus",
                Params.newParams().done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

    }

    @Test
    public void allDomNames() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/allDomNames",
                Params.newParams().done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(json.getIntValue("count"), json.getJSONArray("doms").size());
    }


    @Test
    public void searchDom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/searchDom",
                Params.newParams()
                        .appendParam("expr", "nacos")
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.getJSONArray("doms").size() > 0);
    }

    @Test
    public void addCluster4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/addCluster4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1)
                        .appendParam("clusterName", NamingBase.TEST_NEW_CLUSTER_4_DOM_1)
                        .appendParam("cktype", "TCP")
                        .appendParam("defIPPort", "1111")
                        .appendParam("defCkport", "2222")
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/api/dom",
                Params.newParams().appendParam("dom", NamingBase.TEST_DOM_1).done(), String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertTrue(response.getBody().contains(NamingBase.TEST_NEW_CLUSTER_4_DOM_1));

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals(NamingBase.TEST_DOM_1, json.getString("name"));

        JSONArray clusters = json.getJSONArray("clusters");
        Assert.assertEquals(2, clusters.size());
        for (int i=0; i<2; i++) {
            JSONObject cluster = clusters.getJSONObject(i);
            if (cluster.getString("name").equals(NamingBase.TEST_NEW_CLUSTER_4_DOM_1)) {

                Assert.assertEquals("1111", cluster.getString("defIPPort"));
                Assert.assertEquals("2222", cluster.getString("defCkport"));

            }
        }
    }

    @Test
    public void domList() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/domList",
                Params.newParams()
                        .appendParam("startPg", "0")
                        .appendParam("pgSize", "10")
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertTrue(json.getJSONArray("domList").size() > 0);
    }

    @Test
    public void distroStatus() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/distroStatus",
                Params.newParams()
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void metrics() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/metrics",
                Params.newParams()
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.getIntValue("domCount") > 0);
        Assert.assertTrue(json.getIntValue("ipCount") > 0);
        Assert.assertTrue(json.getIntValue("responsibleDomCount") > 0);
        Assert.assertTrue(json.getIntValue("responsibleIPCount") > 0);
    }

    @Test
    public void updateClusterConf() throws Exception {
        // TODO
    }

    @Test
    public void reCalculateCheckSum4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/reCalculateCheckSum4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void getDomString4MD5() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/getDomString4MD5",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void getResponsibleServer4Dom() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/getResponsibleServer4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void domServeStatus() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/api/domServeStatus",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .done(),
                String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.getBooleanValue("success"));
        Assert.assertTrue(json.getJSONObject("data").getJSONArray("ips").size() > 0);
    }

    private <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
                .queryParams(params);

        return this.restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, clazz);
    }

    private void prepareData() {

        ResponseEntity<String> responseEntity = request("/nacos/v1/ns/api/regDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("cktype", "TCP")
                        .appendParam("token", "abc")
                        .done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: register domain failed!" + responseEntity.toString());
        }

        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        responseEntity = request("/nacos/v1/ns/api/addIP4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("ipList", NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT_4_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1).done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: add ip for domain failed!" + responseEntity.toString());
        }
    }

    private void removeData() {

        ResponseEntity<String> responseEntity = request("/nacos/v1/ns/api/remvDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("token", "abc")
                        .done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: remove domain failed!" + responseEntity.toString());
        }
    }

}
