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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingMaintainFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;

/**
 * @author liaochuntao
 * @date 2019-05-07 10:13
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NamingMaintainService_ITCase {

    private NamingMaintainService namingMaintainService;
    private NamingService namingService;
    private Instance instance;
    private String serviceName;

    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {

        NamingBase.prepareServer(port);

        if (namingMaintainService == null) {
            TimeUnit.SECONDS.sleep(10);
            namingMaintainService = NamingMaintainFactory.createMaintainService("127.0.0.1" + ":" + port);
        }

        if (namingService == null) {
            TimeUnit.SECONDS.sleep(10);
            namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }

        instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external");
        map.put("version", "1.0");
        instance.setMetadata(map);

        serviceName = randomDomainName();

    }

    @Test
    public void updateInstance() throws NacosException, InterruptedException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        instance.setMetadata(map);
        namingMaintainService.updateInstance(serviceName, instance);
        TimeUnit.SECONDS.sleep(3L);
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        Assert.assertEquals(instances.size(), 1);
        Assert.assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        System.out.println(instances.get(0));
    }

    @Test
    public void updateInstanceWithDisable() throws NacosException, InterruptedException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        instance.setMetadata(map);
        instance.setEnabled(false);
        namingMaintainService.updateInstance(serviceName, instance);
        TimeUnit.SECONDS.sleep(3L);
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        Assert.assertEquals(0, instances.size());
    }

    @Test
    public void createAndUpdateService() throws NacosException {
        String serviceName = randomDomainName();
        // register service
        Service preService = new Service();
        preService.setName(serviceName);
        preService.setGroupName(Constants.DEFAULT_GROUP);
        preService.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put(serviceName, "this is a register metadata");
        preService.setMetadata(metadata);
        ExpressionSelector selector = new ExpressionSelector();
        selector.setExpression("CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");

        System.out.println("service info : " + preService);
        namingMaintainService.createService(preService, selector);
        Service remoteService = namingMaintainService.queryService(serviceName);
        System.out.println("remote service info : " + remoteService);
        Assert.assertEquals(preService.toString(), remoteService.toString());

        // update service
        Service nowService = new Service();
        nowService.setName(serviceName);
        nowService.setGroupName(Constants.DEFAULT_GROUP);
        nowService.setProtectThreshold(1.0f);
        metadata.clear();
        metadata.put(serviceName, "this is a update metadata");
        nowService.setMetadata(metadata);

        namingMaintainService.updateService(nowService, new NoneSelector());
        remoteService = namingMaintainService.queryService(serviceName);
        System.out.println("remote service info : " + remoteService);
        Assert.assertEquals(nowService.toString(), remoteService.toString());
    }

    @Test
    public void deleteService() throws NacosException {
        String serviceName = randomDomainName();
        Service preService = new Service();
        preService.setName(serviceName);
        System.out.println("service info : " + preService);
        namingMaintainService.createService(preService, new NoneSelector());

        Assert.assertTrue(namingMaintainService.deleteService(serviceName));
    }
    
    @After
    public void tearDown() throws NacosException {
        namingMaintainService.shutDown();
        namingService.shutDown();
    }
}
