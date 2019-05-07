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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.MaintainFactory;
import com.alibaba.nacos.api.naming.MaintainService;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.naming.NamingApp;
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

/**
 * @author liaochuntao
 * @date 2019-05-07 10:13
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MaintainService_ITCase {

    private MaintainService maintainService;
    private NamingService namingService;
    private Instance instance;
    private Service service;

    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {

        NamingBase.prepareServer(port);

        if (maintainService == null) {
            TimeUnit.SECONDS.sleep(10);
            maintainService = MaintainFactory.createMaintainService("127.0.0.1" + ":" + port);
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

        service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "this is a test metadata");
        service.setMetadata(metadata);
    }

    @Test
    public void updateInstance() throws NacosException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        instance.setMetadata(map);
        namingService.registerInstance("nacos-api", instance);
        maintainService.updateInstance("nacos-api", instance);
        List<Instance> instances = namingService.getAllInstances("nacos-api", true);

        Assert.assertEquals(instances.size(), 1);
        System.out.println(instances.get(0));
    }

    @Test
    public void createService() throws NacosException {

        ExpressionSelector selector = new ExpressionSelector();
        selector.setExpression("CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");

        System.out.println("service info : " + service);
        maintainService.createService(service, selector);
        Service remoteService = maintainService.queryService("nacos-api");
        System.out.println("remote service info : " + remoteService);
        Assert.assertEquals(service.toString(), remoteService.toString());
    }

    @Test
    public void updateService() throws NacosException {
        Service service = new Service();
        service.setName("nacos-api");
        service.setGroupName(Constants.DEFAULT_GROUP);
        service.setProtectThreshold(1.0f);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("nacos-1", "nacos-3-update");
        service.setMetadata(metadata);

        maintainService.updateService(service, new NoneSelector());
        Service remoteService = maintainService.queryService("nacos-api");
        System.out.println("remote service info : " + remoteService);
        Assert.assertEquals(service.toString(), remoteService.toString());
    }

    @Test
    public void deleteService() throws NacosException {
        Assert.assertTrue(maintainService.deleteService("nacos-api"));
    }

    @Test
    public void dregInstance() throws NacosException {
        namingService.deregisterInstance("nacos-api", "127.0.0.1", 8081);
    }

}
