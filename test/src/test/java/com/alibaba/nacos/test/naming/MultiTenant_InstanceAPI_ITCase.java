package com.alibaba.nacos.test.naming;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.naming.NamingApp;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MultiTenant_InstanceAPI_ITCase {

    private NamingService naming;
    private NamingService naming1;
    private NamingService naming2;
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private URL base;

    private volatile List<Instance> instances = Collections.emptyList();

    @Before
    public void init() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);

        naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, "namespace-1");
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        naming1 = NamingFactory.createNamingService(properties);


        properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, "namespace-2");
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        naming2 = NamingFactory.createNamingService(properties);
    }

    /**
     * @TCDescription : 多租户注册IP，listInstance接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_listInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        String url = "/nacos/v1/ns/instance/list";
        ResponseEntity<String> response = request(url,
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("namespaceId", "namespace-1")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());

        Assert.assertEquals("11.11.11.11", json.getJSONArray("hosts").getJSONObject(0).getString("ip"));

        response = request(url,
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JSON.parseObject(response.getBody());

        Assert.assertEquals(2, json.getJSONArray("hosts").size());
    }

    /**
     * @TCDescription : 多租户注册IP，getInstance接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_getInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        ResponseEntity<String> response = request("/nacos/v1/ns/instance",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "33.33.33.33")   //错误的IP，隔离验证
                .appendParam("port", "8888")
                .appendParam("namespaceId", "namespace-2")
                .done(),
            String.class);
        Assert.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "33.33.33.33")
                .appendParam("port", "8888")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(2, json.getJSONArray("hosts").size());
    }

    /**
     * @TCDescription : 多租户注册IP，deleteInstance接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_deleteInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        ResponseEntity<String> response = request("/nacos/v1/ns/instance",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "33.33.33.33")
                .appendParam("port", "8888")
                .appendParam("namespaceId", "namespace-1") //删除namespace-1中没有的IP
                .done(),
            String.class,
            HttpMethod.DELETE);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(2, json.getJSONArray("hosts").size());
    }

    /**
     * @TCDescription : 多租户注册IP，putInstance接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_putInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        ResponseEntity<String> response = request("/nacos/v1/ns/instance",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "33.33.33.33")
                .appendParam("port", "8888")
                .appendParam("namespaceId", "namespace-1") //新增
                .done(),
            String.class,
            HttpMethod.PUT);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-1")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(2, json.getJSONArray("hosts").size());

        //namespace-2个数
        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-2")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JSON.parseObject(response.getBody());
        System.out.println(json);
        Assert.assertEquals(1, json.getJSONArray("hosts").size());
    }

    /**
     * @TCDescription : 多租户注册IP，update一个没有的实例接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_updateInstance_notExsitInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        ResponseEntity<String> response = request("/nacos/v1/ns/instance",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "33.33.33.33")
                .appendParam("port", "8888")
                .appendParam("namespaceId", "namespace-1") //新增
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-1")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(2, json.getJSONArray("hosts").size());

        //namespace-2个数
        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-2")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JSON.parseObject(response.getBody());
        System.out.println(json);
        Assert.assertEquals(1, json.getJSONArray("hosts").size());
    }

    /**
     * @TCDescription : 多租户注册IP，update一个已有的实例接口
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_updateInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        ResponseEntity<String> response = request("/nacos/v1/ns/instance",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("ip", "11.11.11.11")
                .appendParam("port", "80")
                .appendParam("namespaceId", "namespace-1") //新增
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-1")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(1, json.getJSONArray("hosts").size());

        //namespace-2个数
        response = request("/nacos/v1/ns/instance/list",
            Params.newParams()
                .appendParam("serviceName", serviceName) //获取naming中的实例
                .appendParam("namespaceId", "namespace-2")
                .done(),
            String.class);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JSON.parseObject(response.getBody());
        Assert.assertEquals(1, json.getJSONArray("hosts").size());
    }

    private void verifyInstanceListForNaming(NamingService naming, int size, String serviceName) throws Exception {
        int i = 0;
        while ( i < 20 ) {
            List<Instance> instances = naming.getAllInstances(serviceName);
            if (instances.size() == size) {
                break;
            } else {
                TimeUnit.SECONDS.sleep(3);
                i++;
            }
        }
    }

    private <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz) {
        return  request(path, params, clazz, HttpMethod.GET);
    }

    private <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz, HttpMethod httpMethod) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), httpMethod, entity, clazz);
    }
}
