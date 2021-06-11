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

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.*;

/**
 * @author nkorange
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class MultiTenant_ITCase {

    private NamingService naming;
    private NamingService naming1;
    private NamingService naming2;
    @LocalServerPort
    private int port;

    private volatile List<Instance> instances = Collections.emptyList();

    @Before
    public void init() throws Exception {
        Thread.sleep(6000L);
        NamingBase.prepareServer(port);

        naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);

        while (true) {
            if (!"UP".equals(naming.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
        }

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
        Assert.assertEquals(1, instances.size());
    }

    /**
     * @TCDescription : 多Group注册实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_multiGroup_registerInstance() throws Exception {
        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, TEST_GROUP_1,"11.11.11.11", 80);

        naming2.registerInstance(serviceName, TEST_GROUP_2,"22.22.22.22", 80);

        naming.registerInstance(serviceName, "33.33.33.33", 8888);

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.getAllInstances(serviceName);
        Assert.assertEquals(0, instances.size());

        instances = naming2.getAllInstances(serviceName, TEST_GROUP_2);
        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("22.22.22.22", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());

        naming1.deregisterInstance(serviceName, TEST_GROUP_1,"11.11.11.11", 80);
        naming1.deregisterInstance(serviceName, TEST_GROUP_2,"22.22.22.22", 80);
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

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.selectInstances(serviceName, true);

        Assert.assertEquals(1, instances.size());
        Assert.assertEquals(TEST_IP_4_DOM_1, instances.get(0).getIp());
        Assert.assertEquals(TEST_PORT, instances.get(0).getPort());

        instances = naming2.selectInstances(serviceName, false);
        Assert.assertEquals(0, instances.size());


        instances = naming.selectInstances(serviceName, true);
        Assert.assertEquals(1, instances.size());
    }

    /**
     * @TCDescription : 多租户,多Group注册IP，port相同的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_equalIP() throws Exception {
        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, TEST_GROUP_1,"11.11.11.11", 80);

        naming2.registerInstance(serviceName, TEST_GROUP_2,"11.11.11.11", 80);

        naming.registerInstance(serviceName, Constants.DEFAULT_GROUP,"11.11.11.11", 80);

        TimeUnit.SECONDS.sleep(5L);

        List<Instance> instances = naming1.getAllInstances(serviceName);

        Assert.assertEquals(0, instances.size());

        instances = naming2.getAllInstances(serviceName, TEST_GROUP_2);

        Assert.assertEquals(1, instances.size());
        Assert.assertEquals("11.11.11.11", instances.get(0).getIp());
        Assert.assertEquals(80, instances.get(0).getPort());

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
    }

    /**
     * @TCDescription : 多租户,多Group注册IP，port相同的实例, 通过group获取实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_getInstances() throws Exception {
        String serviceName = randomDomainName();
        System.out.println(serviceName);
        naming1.registerInstance(serviceName, TEST_GROUP_1,"11.11.11.11", 80);
        // will cover the instance before
        naming1.registerInstance(serviceName, TEST_GROUP_2,"11.11.11.11", 80);

        naming.registerInstance(serviceName, Constants.DEFAULT_GROUP,"11.11.11.11", 80);

        TimeUnit.SECONDS.sleep(5L);
        List<Instance> instances = naming1.getAllInstances(serviceName, TEST_GROUP);

        Assert.assertEquals(0, instances.size());

        instances = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, instances.size());
        naming1.deregisterInstance(serviceName, TEST_GROUP_1,"11.11.11.11", 80);
        naming1.deregisterInstance(serviceName, TEST_GROUP_2,"11.11.11.11", 80);
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

        ListView<String> listView = naming1.getServicesOfServer(1, 200);

        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(5L);
        ListView<String> listView1 = naming1.getServicesOfServer(1, 200);
        Assert.assertEquals(listView.getCount(), listView1.getCount());
    }

    /**
     * @TCDescription : 多租户, 多group，同服务获取实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_getServicesOfServer() throws Exception {

        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, TEST_GROUP_1, "11.11.11.11",  TEST_PORT, "c1");
        // will cover the instance before
        naming1.registerInstance(serviceName, TEST_GROUP_2, "22.22.22.22",  TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(5L);

        //服务不会删除，实例会注销
        ListView<String> listView = naming1.getServicesOfServer(1, 20, TEST_GROUP_1);

        naming2.registerInstance(serviceName, "33.33.33.33", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(5L);
        ListView<String> listView1 = naming1.getServicesOfServer(1, 20, TEST_GROUP_1);
        Assert.assertEquals(listView.getCount(), listView1.getCount());
        Assert.assertNotEquals(0, listView1.getCount());
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
     * @TCDescription : 多租户多group订阅服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_subscribe() throws Exception {

        String serviceName = randomDomainName();

        naming1.subscribe(serviceName, TEST_GROUP_1, new EventListener() {
            @Override
            public void onEvent(Event event) {
                instances = ((NamingEvent) event).getInstances();
            }
        });

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, TEST_GROUP_1,"33.33.33.33", TEST_PORT, "c1");

        while (instances.size() == 0) {
            TimeUnit.SECONDS.sleep(1L);
        }
        TimeUnit.SECONDS.sleep(2L);
        Assert.assertEquals(1, instances.size());

        TimeUnit.SECONDS.sleep(2L);
        Assert.assertTrue(verifyInstanceList(instances, naming1.getAllInstances(serviceName, TEST_GROUP_1)));

        naming1.deregisterInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.deregisterInstance(serviceName, TEST_GROUP_1,"33.33.33.33", TEST_PORT, "c1");
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
     * @TCDescription : 多租户,多group下, 没有对应的group订阅，取消订阅服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_nosubscribe_unSubscribe() throws Exception {

        String serviceName = randomDomainName();
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                instances = ((NamingEvent)event).getInstances();
            }
        };

        naming1.subscribe(serviceName, TEST_GROUP_1, listener);
        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, TEST_GROUP_2,"33.33.33.33", TEST_PORT, "c1");

        TimeUnit.SECONDS.sleep(3L);
        Assert.assertEquals(serviceName, naming1.getSubscribeServices().get(0).getName());
        Assert.assertEquals(0, naming2.getSubscribeServices().size());

        naming1.unsubscribe(serviceName, listener);    //取消订阅服务，没有订阅group
        TimeUnit.SECONDS.sleep(3L);
        Assert.assertEquals(1, naming1.getSubscribeServices().size());

        naming1.unsubscribe(serviceName, TEST_GROUP_1, listener);   //取消订阅服务，有订阅group
        TimeUnit.SECONDS.sleep(3L);
        Assert.assertEquals(0, naming1.getSubscribeServices().size());

        Assert.assertEquals(0, naming2.getSubscribeServices().size());
    }

    /**
     * @TCDescription : 多租户,多group下, 多个group订阅，查看服务的个数
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_unSubscribe() throws Exception {

        String serviceName = randomDomainName();
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(((NamingEvent)event).getServiceName());
                instances = ((NamingEvent)event).getInstances();
            }
        };

        naming1.subscribe(serviceName, Constants.DEFAULT_GROUP, listener);
        naming1.subscribe(serviceName, TEST_GROUP_2, listener);
        naming1.subscribe(serviceName, TEST_GROUP_1, listener);

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, TEST_GROUP_2,"33.33.33.33", TEST_PORT, "c1");

        while (instances.size() == 0) {
            TimeUnit.SECONDS.sleep(1L);
        }
        TimeUnit.SECONDS.sleep(2L);
        Assert.assertEquals(serviceName, naming1.getSubscribeServices().get(0).getName());
        Assert.assertEquals(3, naming1.getSubscribeServices().size());

        naming1.unsubscribe(serviceName, listener);
        naming1.unsubscribe(serviceName, TEST_GROUP_2, listener);
        TimeUnit.SECONDS.sleep(3L);
        Assert.assertEquals(1, naming1.getSubscribeServices().size());
        Assert.assertEquals(TEST_GROUP_1, naming1.getSubscribeServices().get(0).getGroupName());

        naming1.unsubscribe(serviceName, TEST_GROUP_1, listener);
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

        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");
        naming2.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 1, serviceName);

        Assert.assertEquals(1, naming1.getAllInstances(serviceName).size());

        naming1.deregisterInstance(serviceName, "22.22.22.22", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(12);

        Assert.assertEquals(0, naming1.getAllInstances(serviceName).size());
        Assert.assertEquals(1, naming2.getAllInstances(serviceName).size());
    }


    /**
     * @TCDescription : 多租户, 多group，删除group不存在的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    @Ignore("nacos 2.0 deregister only judged by client and service")
    public void multipleTenant_group_deregisterInstance() throws Exception {

        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c2");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 2, serviceName);

        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());

        naming1.deregisterInstance(serviceName, TEST_GROUP_2,"22.22.22.22", TEST_PORT, "c1");
        TimeUnit.SECONDS.sleep(12);

        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());
    }

    /**
     * @TCDescription : 多租户, 多group，删除clusterName不存在的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    @Ignore("nacos 2.0 deregister only judged by client and service")
    public void multipleTenant_group_cluster_deregisterInstance() throws Exception {

        String serviceName = randomDomainName();

        naming1.registerInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c2");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 2, serviceName);

        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());

        naming1.deregisterInstance(serviceName, "22.22.22.22", TEST_PORT);
        TimeUnit.SECONDS.sleep(3L);
        Assert.assertEquals(2, naming1.getAllInstances(serviceName).size());

        naming1.deregisterInstance(serviceName, "11.11.11.11", TEST_PORT, "c1");
        naming1.deregisterInstance(serviceName, "22.22.22.22", TEST_PORT, "c2");
    }

    /**
     * @TCDescription : 多租户下，选择一个健康的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_selectOneHealthyInstance() throws Exception {

        String serviceName = randomDomainName();
        
        naming1.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c2");
        naming2.registerInstance(serviceName, "22.22.22.22", TEST_PORT, "c3");

        List<Instance> instances = naming1.getAllInstances(serviceName);
        verifyInstanceListForNaming(naming1, 1, serviceName);

        Assert.assertEquals(1, naming1.getAllInstances(serviceName).size());

        Instance instance = naming1.selectOneHealthyInstance(serviceName, Arrays.asList("c2"));
        Assert.assertEquals("22.22.22.22", instance.getIp());
    
        naming2.deregisterInstance(serviceName, "22.22.22.22", TEST_PORT, "c3");
        TimeUnit.SECONDS.sleep(5);
        instance = naming1.selectOneHealthyInstance(serviceName);
        Assert.assertEquals("22.22.22.22", instance.getIp());
    }

    /**
     * @TCDescription : 多租户下，多group下，选择一个健康的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void multipleTenant_group_selectOneHealthyInstance() throws Exception {

        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, TEST_GROUP, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, TEST_GROUP_1,"22.22.22.22", TEST_PORT, "c2");
        naming1.registerInstance(serviceName, TEST_GROUP_2,"33.33.33.33", TEST_PORT, "c3");

        List<Instance> instances = naming1.getAllInstances(serviceName, TEST_GROUP);
        verifyInstanceListForNaming(naming1, 0, serviceName);

        Assert.assertEquals(0, naming1.getAllInstances(serviceName).size());   //defalut group

        Instance instance = naming1.selectOneHealthyInstance(serviceName, TEST_GROUP, Arrays.asList("c1"));
        Assert.assertEquals("11.11.11.11", instance.getIp());

        instance = naming1.selectOneHealthyInstance(serviceName, TEST_GROUP_1);
        Assert.assertEquals("22.22.22.22", instance.getIp());

        naming1.deregisterInstance(serviceName, TEST_GROUP, "11.11.11.11", TEST_PORT, "c1");
        naming1.deregisterInstance(serviceName, TEST_GROUP_1,"22.22.22.22", TEST_PORT, "c2");
        naming1.deregisterInstance(serviceName, TEST_GROUP_2,"33.33.33.33", TEST_PORT, "c3");

    }

    /**
     * @TCDescription : 多租户下，多group下，选择group不存在，一个健康的实例
     * @TestStep :
     * @ExpectResult :
     */
    @Test(expected = IllegalStateException.class)
    public void multipleTenant_noGroup_selectOneHealthyInstance() throws Exception {

        String serviceName = randomDomainName();
        naming1.registerInstance(serviceName, TEST_GROUP, "11.11.11.11", TEST_PORT, "c1");
        naming1.registerInstance(serviceName, TEST_GROUP_1,"22.22.22.22", TEST_PORT, "c2");

        List<Instance> instances = naming1.getAllInstances(serviceName, TEST_GROUP);
        verifyInstanceListForNaming(naming1, 0, serviceName);

        Instance instance = naming1.selectOneHealthyInstance(serviceName, Arrays.asList("c1"));

        naming1.deregisterInstance(serviceName, TEST_GROUP, "11.11.11.11", TEST_PORT, "c1");
        naming1.deregisterInstance(serviceName, TEST_GROUP_1,"22.22.22.22", TEST_PORT, "c2");

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
