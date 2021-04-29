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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.NamingApp;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.DataStore;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.misc.NamingProxy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Ignore("Http sync will stop for 2.0 server, and will be removed")
public class NamingHttpClientProxy_ITCase {
    @LocalServerPort
    private int port;
    @Autowired
    private Serializer serializer;
    private final DataStore dataStore = new DataStore();
    private NamingService namingService;
    private final String namespaceId = "dev";
    private final String serviceName = "biz";
    private final String groupName = "DEFAULT_GROUP";

    @Before
    public void init() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, namespaceId);
        properties.put(PropertyKeyConst.SERVER_ADDR, "localhost:" + port);
        namingService = NamingFactory.createNamingService(properties);
    }

    @Test
    public void testSyncData() throws NacosException, InterruptedException {
        // write data to DataStore
        String groupedName = NamingUtils.getGroupedName(serviceName, groupName);
        Instances instances = new Instances();
        Instance instance = new Instance();
        instance.setIp("1.2.3.4");
        instance.setPort(8888);
        instance.setEphemeral(true);
        instance.setServiceName(groupedName);
        List<Instance> instanceList = new ArrayList<Instance>(1);
        instanceList.add(instance);
        instances.setInstanceList(instanceList);
        String key = KeyBuilder.buildInstanceListKey(namespaceId, instance.getServiceName(), true);

        Datum<Instances> datum = new Datum<>();
        datum.value = instances;
        datum.key = key;
        datum.timestamp.incrementAndGet();
        this.dataStore.put(key, datum);

        // sync data to server
        Map<String, Datum> dataMap = dataStore.getDataMap();
        byte[] content = serializer.serialize(dataMap);
        boolean result = NamingProxy.syncData(content, "localhost:" + port);
        if (!result) {
            Assert.fail("NamingProxy.syncData error");
        }

        // query instance by api
        List<com.alibaba.nacos.api.naming.pojo.Instance> allInstances = namingService.getAllInstances(serviceName, false);
        for (int i = 0; i < 3 && allInstances.isEmpty(); i++) {
            // wait for async op
            TimeUnit.SECONDS.sleep(100);
            allInstances = namingService.getAllInstances(serviceName, false);
        }
        if (allInstances.isEmpty()) {
            Assert.fail("instance is empty");
        }

        com.alibaba.nacos.api.naming.pojo.Instance dst = allInstances.get(0);
        Assert.assertEquals(instance.getIp(), dst.getIp());
        Assert.assertEquals(instance.getPort(), dst.getPort());
        Assert.assertEquals(instance.getServiceName(), dst.getServiceName());
    }

}
