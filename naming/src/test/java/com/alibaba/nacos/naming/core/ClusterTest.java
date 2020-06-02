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
package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchDomain.TcpHealthParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author nkorange
 */
@RunWith(MockitoJUnitRunner.class)
public class ClusterTest {

    private Cluster cluster;

    @Mock
    private ConfigurableApplicationContext context;

    @Mock
    private SwitchDomain switchDomain;

    @Before
    public void before() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(SwitchDomain.class)).thenReturn(switchDomain);
        when(switchDomain.getTcpHealthParams()).thenReturn(new TcpHealthParams());
        Service service = new Service();
        service.setName("nacos.service.1");

        cluster = new Cluster("nacos-cluster-1", service);
        cluster.setDefCkport(80);
        cluster.setDefIPPort(8080);
        cluster.init();
    }


    @Test
    public void updateCluster() {
        Service service = new Service();
        service.setName("nacos.service.2");

        Cluster newCluster = new Cluster("nacos-cluster-1", service);
        newCluster.setDefCkport(8888);
        newCluster.setDefIPPort(9999);
        Http healthCheckConfig = new Http();
        healthCheckConfig.setPath("/nacos-path-1");
        healthCheckConfig.setExpectedResponseCode(500);
        healthCheckConfig.setHeaders("Client-Version:nacos-test-1");
        newCluster.setHealthChecker(healthCheckConfig);

        cluster.update(newCluster);

        assertEquals(8888, cluster.getDefCkport());
        assertEquals(9999, cluster.getDefIPPort());
        assertTrue(cluster.getHealthChecker() instanceof Http);
        Http httpHealthCheck = (Http) (cluster.getHealthChecker());
        assertEquals("/nacos-path-1", httpHealthCheck.getPath());
        assertEquals(500, httpHealthCheck.getExpectedResponseCode());
        assertEquals("Client-Version:nacos-test-1", httpHealthCheck.getHeaders());
    }

    @Test
    public void updateIps() {

        Instance instance1 = new Instance();
        instance1.setIp("1.1.1.1");
        instance1.setPort(1234);

        Instance instance2 = new Instance();
        instance2.setIp("1.1.1.1");
        instance2.setPort(2345);

        List<Instance> list = new ArrayList<>();
        list.add(instance1);
        list.add(instance2);

        cluster.updateIPs(list, false);

        List<Instance> ips = cluster.allIPs();
        assertNotNull(ips);
        assertEquals(2, ips.size());
        assertEquals("1.1.1.1", ips.get(0).getIp());
        assertEquals(1234, ips.get(0).getPort());
        assertEquals("1.1.1.1", ips.get(1).getIp());
        assertEquals(2345, ips.get(1).getPort());
    }

    @Test
    public void testValidate() {
        Service service = new Service("nacos.service.2");
        cluster = new Cluster("nacos-cluster-1", service);
        cluster.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateClusterNameNull() {
        Service service = new Service("nacos.service.2");
        cluster = new Cluster(null, service);
        cluster.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateServiceNull() {
        cluster = new Cluster("nacos-cluster-1", null);
        cluster.validate();
    }

    @Test
    public void testSerialize() throws Exception {
        String actual = JacksonUtils.toJson(cluster);
        System.out.println(actual);
        assertTrue(actual.contains("\"defaultPort\":80"));
        assertTrue(actual.contains("\"defIPPort\":8080"));
        assertTrue(actual.contains("\"healthChecker\":{\"type\":\"TCP\"}"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"defCkport\":80"));
        assertTrue(actual.contains("\"name\":\"nacos-cluster-1\""));
        assertTrue(actual.contains("\"defaultCheckPort\":80"));
        assertTrue(actual.contains("\"serviceName\":\"nacos.service.1\""));
        assertTrue(actual.contains("\"useIPPort4Check\":true"));
        assertTrue(actual.contains("\"sitegroup\":\"\""));
        assertTrue(actual.contains("\"empty\":true"));
        assertFalse(actual.contains("\"service\""));

    }

    @Test
    public void testDeserialize() throws Exception {
        String example = "{\"defCkport\":80,\"defIPPort\":8080,\"defaultCheckPort\":80,\"defaultPort\":80,\"empty\":true,\"healthChecker\":{\"type\":\"TCP\"},\"metadata\":{},\"name\":\"nacos-cluster-1\",\"serviceName\":\"nacos.service.1\",\"sitegroup\":\"\",\"useIPPort4Check\":true}";
        Cluster actual = JacksonUtils.toObj(example, Cluster.class);
        assertEquals(80, actual.getDefCkport());
        assertEquals(8080, actual.getDefIPPort());
        assertEquals(80, actual.getDefaultCheckPort());
        assertEquals(80, actual.getDefaultPort());
        assertTrue(actual.isEmpty());
        assertTrue(actual.getMetadata().isEmpty());
        assertTrue(actual.isUseIPPort4Check());
        assertEquals("nacos-cluster-1", actual.getName());
        assertEquals("nacos.service.1", actual.getServiceName());
        assertEquals("", actual.getSitegroup());
        assertNull(actual.getHealthCheckTask());
    }
}
