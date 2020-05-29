package com.alibaba.nacos.common.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * AsyncHttpUtils
 *
 * @author mai.jh
 * @date 2020/5/29
 */
public class AsyncHttpUtils<T> {

    private NacosAsyncRestTemplate nacosRestTemplate = HttpClientManager.getNacosAsyncRestTemplate();

    private final String CONFIG_CONTROLLER_PATH = "/nacos/v1/cs";
    private final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    private final String IP = "http://localhost:8848";

    @Test
    public void test_url_get() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        nacosRestTemplate.get(url, Header.newInstance(), query, Map.class, new Callback<Map>() {
            @Override
            public void onReceive(RestResult<Map> result) {
                System.out.println(JSON.toJSONString(result));
//                Assert.assertTrue(result.ok());
//                Assert.assertEquals(result.getData().get("dom"), "app-test");
            }

            @Override
            public void onError(Throwable throwable) {

            }
        });
        Thread.sleep(20000);

    }

    @Test
    public void test_url_post_from() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        nacosRestTemplate.postFrom(url, Header.newInstance(), Query.newInstance(), param, String.class, new Callback<String>() {
            @Override
            public void onReceive(RestResult<String> result) {
                System.out.println(JSON.toJSONString(result));
//                Assert.assertTrue(result.ok());
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
            }
        });
       Thread.sleep(20000);
    }


}
