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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author liaochuntao
 * @date 2019-05-07 10:13
 **/
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class NamingMaintainService_ITCase {
    
    private NamingMaintainService namingMaintainService;
    
    private NamingService namingService;
    
    private Instance instance;
    
    private String serviceName;
    
    @LocalServerPort
    private int port;
    
    @BeforeEach
    void init() throws Exception {
        
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
    void updateInstance() throws NacosException, InterruptedException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        instance.setMetadata(map);
        namingMaintainService.updateInstance(serviceName, instance);
        TimeUnit.SECONDS.sleep(3L);
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        assertEquals(1, instances.size());
        assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        System.out.println(instances.get(0));
    }
    
    @Test
    void updateInstanceWithDisable() throws NacosException, InterruptedException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        instance.setMetadata(map);
        instance.setEnabled(false);
        namingMaintainService.updateInstance(serviceName, instance);
        TimeUnit.SECONDS.sleep(3L);
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        assertEquals(0, instances.size());
    }
    
    @Test
    void createAndUpdateService() throws NacosException {
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
        assertEquals(preService.toString(), remoteService.toString());
        
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
        assertEquals(nowService.toString(), remoteService.toString());
    }
    
    @Test
    void deleteService() throws NacosException {
        String serviceName = randomDomainName();
        Service preService = new Service();
        preService.setName(serviceName);
        System.out.println("service info : " + preService);
        namingMaintainService.createService(preService, new NoneSelector());
        
        assertTrue(namingMaintainService.deleteService(serviceName));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        namingMaintainService.shutDown();
        namingService.shutDown();
    }
}
