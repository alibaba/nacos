package com.alibaba.nacos.common.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.common.reflect.TypeToken;
import org.junit.Test;
import sun.tools.jstat.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HttpUtilsTest<T> {

    private NacosRestTemplate nacosRestTemplate = HttpClientManager.getNacosRestTemplate();


    private final String CONFIG_CONTROLLER_PATH = "/nacos/v1/cs";
    private final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    private final String IP = "http://localhost:8848";


    @Test
    public void test_url_post() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Query query = Query.newInstance()
            .addParam("ip", "11.11.11.11")
            .addParam("port", "8080")
            .addParam("serviceName", "app-test");

        RestResult post = nacosRestTemplate.post(url, Header.newInstance(), query, null, RestResult.class);
        System.out.println(JSON.toJSONString(post));
    }

    @Test
    public void test_url_get() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        RestResult<Map> get = nacosRestTemplate.get(url, Header.newInstance(), query, Map.class);
        System.out.println(JSON.toJSONString(get));
    }

}
