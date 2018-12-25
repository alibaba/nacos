package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
//    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MultipleTenantTest {

    private NamingService naming;
    private NamingService naming1;
    private NamingService naming2;
    @LocalServerPort
    private int port;

    private volatile List<Instance> instances = Collections.emptyList();

    @Before
    public void init() throws Exception {

        TimeUnit.SECONDS.sleep(10);

        port = 8848;

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

    @Test
    public void registerInstance() throws Exception {

        String serviceName = randomDomainName();

        System.out.println(serviceName);

        naming1.registerInstance(serviceName, "11.11.11.11", 80);

        naming2.registerInstance(serviceName, "22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);
        naming.registerInstance(serviceName, "44.44.44.44", 8888);

        TimeUnit.SECONDS.sleep(8L);

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

        TimeUnit.SECONDS.sleep(10000000L);
    }

    @Test
    public void subscribeAdd() throws Exception {

        String serviceName = randomDomainName();

        naming1.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent) event).getServiceName());
                System.out.println(((NamingEvent) event).getInstances());
                instances = ((NamingEvent) event).getInstances();
            }
        });

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");

        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "44.44.44.44", TEST_PORT, "c1");

        while (instances.size() != 2) {
            Thread.sleep(1000L);
        }

        Set<String> ips = new HashSet<String>();
        ips.add(instances.get(0).getIp());
        ips.add(instances.get(1).getIp());
        Assert.assertTrue(ips.contains("11.11.11.11"));
        Assert.assertTrue(ips.contains("22.22.22.22"));

        Assert.assertTrue(verifyInstanceList(instances, naming1.getAllInstances(serviceName)));
    }
}
