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
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
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
 * @author githucheng2978.
 * @date .
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos",
        "nacos.remote.server.grpc.tls.enableTls=true",
        "nacos.remote.server.grpc.tls.compatibility=false",
        "nacos.remote.server.grpc.tls.certificateChainFile=server-cert.pem",
        "nacos.remote.server.grpc.tls.certChainFile=server-key.pem",
},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NamingTlsServiceTls_ITCase {


    @LocalServerPort
    private int port;

    @Test(expected = NacosException.class)
    public void TlsServerAndPlainClient() throws NacosException {

        Instance   instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        NamingService namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(randomDomainName(), instance);
        namingService.shutDown();

    }

    @Test
    public void TlsServerAndTlsClientTrustCa() throws NacosException {
        String serviceName = randomDomainName();
        System.setProperty("nacos.remote.client.grpc.enable.tls","true");
        System.setProperty("nacos.remote.client.grpc.tls.trustCollectionChainPath","ca-cert.pem");
        Instance   instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        NamingService namingService  = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        try {
            TimeUnit.SECONDS.sleep(3L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        Assert.assertEquals(instances.size(), 1);
        Assert.assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        namingService.shutDown();

    }

    @Test
    public void TlsServerAndTlsClientAll() throws NacosException {
        String serviceName = randomDomainName();
        System.setProperty(GrpcConstants.GRPC_CLIENT_ENABLE_TLS,"true");
        System.setProperty(GrpcConstants.GRPC_CLIENT_ENABLE_TLS_TRUST_ALL,"true");
        Instance   instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        NamingService namingService  = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        namingService.registerInstance(serviceName, instance);
        try {
            TimeUnit.SECONDS.sleep(3L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        Assert.assertEquals(instances.size(), 1);
        Assert.assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        System.out.println(instances.get(0));
        namingService.shutDown();
    }
}
