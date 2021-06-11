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
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.test.base.Params;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ClientBeat_ITCase extends NamingBase {

    private NamingService naming;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        NamingBase.prepareServer(port);
        if (naming == null) {
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
        while (true) {
            if (!"UP".equals(naming.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
        }
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
    }

    @Test
    public void testLightBeat() throws Exception {

        String serviceName = randomDomainName();

        naming.registerInstance(serviceName, "1.2.3.4", 81);

        Instance instance = new Instance();
        instance.setIp("1.2.3.4");
        instance.setPort(80);
        instance.addMetadata("k1", "v1");
        instance.addMetadata("k2", "v2");
        naming.registerInstance(serviceName, instance);

        TimeUnit.SECONDS.sleep(2L);

        List<Instance> list = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, list.size());
        for (Instance instance1 : list) {
            Assert.assertEquals("1.2.3.4", instance1.getIp());
            Assert.assertTrue(instance1.getPort() == 80 || instance1.getPort() == 81);
            if (instance1.getPort() == 80) {
                Assert.assertEquals("v1", instance1.getMetadata().getOrDefault("k1", StringUtils.EMPTY));
                Assert.assertEquals("v2", instance1.getMetadata().getOrDefault("k2", StringUtils.EMPTY));
            }
        }

        // Sleep 35 seconds and see if instance list not changed:
        TimeUnit.SECONDS.sleep(35L);

        list = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, list.size());
        for (Instance instance1 : list) {
            Assert.assertEquals("1.2.3.4", instance1.getIp());
            Assert.assertTrue(instance1.getPort() == 80 || instance1.getPort() == 81);
            if (instance1.getPort() == 80) {
                Assert.assertEquals("v1", instance1.getMetadata().getOrDefault("k1", StringUtils.EMPTY));
                Assert.assertEquals("v2", instance1.getMetadata().getOrDefault("k2", StringUtils.EMPTY));
            }
        }

        // Change the light beat switch of server:
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/operator/switches",
            Params.newParams()
                .appendParam("entry", "lightBeatEnabled")
                .appendParam("value", "false")
                .done(),
            String.class,
            HttpMethod.PUT);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Sleep 35 seconds and see if instance list not changed:
        TimeUnit.SECONDS.sleep(35L);

        list = naming.getAllInstances(serviceName);
        Assert.assertEquals(1, list.size());
        for (Instance instance1 : list) {
            Assert.assertEquals("1.2.3.4", instance1.getIp());
            Assert.assertTrue(instance1.getPort() == 80 || instance1.getPort() == 81);
            if (instance1.getPort() == 80) {
                Assert.assertEquals("v1", instance1.getMetadata().getOrDefault("k1", StringUtils.EMPTY));
                Assert.assertEquals("v2", instance1.getMetadata().getOrDefault("k2", StringUtils.EMPTY));
            }
        }

        // Reset the light beat switch of server:
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/operator/switches",
            Params.newParams()
                .appendParam("entry", "lightBeatEnabled")
                .appendParam("value", "true")
                .done(),
            String.class,
            HttpMethod.PUT);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }
}
