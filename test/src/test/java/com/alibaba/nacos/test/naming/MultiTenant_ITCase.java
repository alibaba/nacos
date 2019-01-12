package com.alibaba.nacos.test.naming;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MultiTenant_ITCase {

    private NamingService naming;
    private NamingService naming1;
    private NamingService naming2;
    @LocalServerPort
    private int port;

    private volatile List<Instance> instances = Collections.emptyList();

    @Before
    public void init() throws Exception {
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
     * @TCDescription : 多租户注册IP，port不相同实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_registerInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("11.11.11.11", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming2.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("22.22.22.22", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(2, instances.size());
    }

    /**
     * @TCDescription : 多租户注册IP，port相同的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_equalIP() throws Exception {
        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "11.11.11.11", 80);

        naming.registerInstance(serviceName, "11.11.11.11", 80);
        naming.registerInstance(serviceName, "11.11.11.11", 80);

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("11.11.11.11", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming2.getAllInstances(serviceName);

        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("11.11.11.11", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
    }

    /**
     * @TCDescription : 多租户注册IP，port相同的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_selectInstances() throws Exception {
        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, TEST_IP_4_DOM_1, TEST_PORT);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, TEST_IP_4_DOM_1, TEST_PORT);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.selectInstances(serviceName, true);

        Assert.assertEquals(1, instances.size());
        Assert.assertEquals(TEST_IP_4_DOM_1, instances.get(0).getIp());
        Assert.assertEquals(TEST_PORT, instances.get(0).getPort());

        instances = naming2.selectInstances(serviceName, false);
        Assert.assertEquals(0, instances.size());


        instances = naming.selectInstances(serviceName, true);
        Assert.assertEquals(2, instances.size());
    }

    /**
     * @TCDescription : 多租户同服务获取实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_getServicesOfServer() throws Exception {

        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(5L);

        ListView<String> listView = naming1.getServicesOfServer(1, 20);

        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(5L);
        ListView<String> listView1 = naming1.getServicesOfServer(1, 20);
        Assert.assertEquals(listView.getCount(), listView1.getCount());
    }

    /**
     * @TCDescription : 多租户订阅服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_subscribe() throws Exception {

        String serviceName = randomDomainName();

        naming1.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                instances = ((NamingEvent) event).getInstances();
            }
        });

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");

        while (instances.size() == 0) {
            TimeUnit.SECONDS.sleep(1L);
        }
        Assert.assertEquals(1, instances.size());

        TimeUnit.SECONDS.sleep(2L);
        Assert.assertTrue(verifyInstanceList(instances, naming1.getAllInstances(serviceName)));
    }

    /**
     * @TCDescription : 多租户取消订阅服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_unSubscribe() throws Exception {

        String serviceName = randomDomainName();
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                instances = ((NamingEvent)event).getInstances();
            }
        };

        naming1.subscribe(serviceName, listener);
        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");

        while (instances.size() == 0) {
            TimeUnit.SECONDS.sleep(1L);
        }
        Assert.assertEquals(serviceName, naming1.getSubscribeServices().get(0).getName());
        Assert.assertEquals(0, naming2.getSubscribeServices().size());

        naming1.unsubscribe(serviceName, listener);

        TimeUnit.SECONDS.sleep(5L);
        Assert.assertEquals(0, naming1.getSubscribeServices().size());
        Assert.assertEquals(0, naming2.getSubscribeServices().size());
    }

    /**
     * @TCDescription : 多租户获取server状态
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_serverStatus() throws Exception {
        Assert.assertEquals(TEST_SERVER_STATUS, naming1.getServerStatus());
        Assert.assertEquals(TEST_SERVER_STATUS, naming2.getServerStatus());
    }

    /**
     * @TCDescription : 多租户删除实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_deregisterInstance() throws Exception {

        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 2, serviceName);

        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());

        naming1.deregisterInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(12);

        Assert.assertEquals(1, naming1.getAllInstances(serviceName).size());
        Assert.assertEquals(1, naming2.getAllInstances(serviceName).size());
    }

    /**
     * @TCDescription : 多租户下，选择一个健康的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_selectOneHealthyInstance() throws Exception {

        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c2");
        naming2.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c3");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 2, serviceName);

        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());

        Instance instance = naming1.selectOneHealthyInstance(serviceName, Arrays.asList("c1"));
        Assert.assertEquals("11.11.11.11", instance.getIp());

        naming1.deregisterInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(12);
        instance = naming1.selectOneHealthyInstance(serviceName);
        Assert.assertEquals("22.22.22.22", instance.getIp());
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
}
