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
import com.github.keran213539.commonOkHttp.CommonOkHttpClient;
import com.github.keran213539.commonOkHttp.CommonOkHttpClientBuilder;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigHistory_ITCase {
    private static final long TIME_OUT = 2000;
    private static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";
    private static final String CONFIG_HISTORY_PATH = "/v1/cs/history";

    private CommonOkHttpClient httpClient = new CommonOkHttpClientBuilder().build();

    @LocalServerPort
    private int port;

    private String SERVER_ADDR = null;

    private HttpAgent agent = null;

    @Before
    public void setUp() throws Exception {
        SERVER_ADDR = "http://127.0.0.1" + ":" + port + "/nacos";

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();
        Map<String, String> prarm = new HashMap<>(7);
        prarm.put("dataId", "testHistory.yml");
        prarm.put("group", "DEFAULT_GROUP");
        prarm.put("content", "test: test");
        prarm.put("desc", "testNoAppname1");
        prarm.put("type", "yaml");
        Assert.assertEquals("true", httpClient.post(SERVER_ADDR + CONFIG_CONTROLLER_PATH , prarm,null));
    }

    @After
    public void cleanup() {
        HttpSimpleClient.HttpResult result;
        try {
            List<String> params1 = Arrays.asList("dataId", "testHistory.yml", "group", "DEFAULT_GROUP", "beta", "false");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params1, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testHistory(){
        String getDataUrl = "?search=accurate&dataId=testHistory.yml&group=DEFAULT_GROUP&&pageNo=1&pageSize=10";
        String queryResult = httpClient.get(SERVER_ADDR + CONFIG_HISTORY_PATH + getDataUrl, null);
        JSONObject resultObj = JSON.parseObject(queryResult);
        JSONArray resultConfigs = resultObj.getJSONArray("pageItems");
        JSONObject config1 = resultConfigs.getJSONObject(0);
        String getDetailUrl = "?nid=" + config1.get("id");
        String result = httpClient.get(SERVER_ADDR + CONFIG_HISTORY_PATH + getDetailUrl, null);
        JSONObject detailObj = JSON.parseObject(result);
        Assert.assertEquals("yaml", detailObj.getString("type"));
    }

}
