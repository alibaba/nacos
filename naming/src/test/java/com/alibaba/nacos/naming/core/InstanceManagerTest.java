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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * @author jifengnan  2019-04-28
 */
public class InstanceManagerTest extends BaseTest {
    @Before
    public void init() {
        super.before();
    }

    @Test
    public void testRegister() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        request.setParameter("ephemeral", "false");
        request.setParameter("ip", IP1);
        request.setParameter("port", "1");
        request.setParameter("healthy", "false");
        request.setParameter("metadata", "{\"key\":\"value\"}");
        request.setParameter(CommonParams.NAMESPACE_ID, TEST_NAMESPACE);
        request.setParameter(CommonParams.CLUSTER_NAME, TEST_CLUSTER_NAME);
        Service service = new Service(TEST_SERVICE_NAME, TEST_NAMESPACE);
        when(serviceManager.createServiceIfAbsent(TEST_NAMESPACE, TEST_SERVICE_NAME, false)).thenReturn(service);
        Instance instance = instanceManager.register(request);
        Assert.assertEquals(IP1, instance.getIp());
        Assert.assertEquals(1, instance.getPort());
        Assert.assertEquals(1, instance.getWeight(), 0.01);
        Assert.assertFalse(instance.isHealthy());
        Assert.assertTrue(instance.isEnabled());
        Assert.assertFalse(instance.isEphemeral());
        Assert.assertEquals("DEFAULT", instance.getApp());
        Assert.assertEquals(1, instance.getMetadata().size());
        Assert.assertEquals("value", instance.getMetadata().get("key"));
        Assert.assertEquals(TEST_CLUSTER_NAME, instance.getCluster().getName());
    }

    @Test
    public void testRegister_serviceNotFound() throws Exception {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("service not found, namespace: public, service: test-service");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        instanceManager.register(request);
    }

    @Test
    public void testUpdate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        request.setParameter("ephemeral", "true");
        request.setParameter("ip", IP1);
        request.setParameter("port", "1");
        request.setParameter("healthy", "false");
        request.setParameter("enabled", "false");
        request.setParameter(CommonParams.NAMESPACE_ID, TEST_NAMESPACE);
        Service service = new Service(TEST_SERVICE_NAME, Constants.DEFAULT_NAMESPACE_ID);
        service.updateIPs(Collections.singletonList(new Instance(IP1, 1, new Cluster(TEST_CLUSTER_NAME, service))), true);
        when(serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME)).thenReturn(service);
        Instance instance = instanceManager.update(request);
        Assert.assertEquals(IP1, instance.getIp());
        Assert.assertEquals(1, instance.getPort());
        Assert.assertEquals(1, instance.getWeight(), 0.01);
        Assert.assertFalse(instance.isHealthy());
        Assert.assertFalse(instance.isEnabled());
        Assert.assertTrue(instance.isEphemeral());
        Assert.assertEquals("DEFAULT", instance.getApp());
        Assert.assertEquals(0, instance.getMetadata().size());
        Assert.assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, instance.getCluster().getName());
    }

    @Test
    public void testUpdate_serviceNotFound() throws Exception {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("service not found, namespace: public, service: test-service");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        instanceManager.update(request);
    }

    @Test
    public void testUpdate_instanceNotFound() throws Exception {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage("instance not exist");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        request.setParameter("ip", IP1);
        request.setParameter("port", "1");
        Service service = new Service(TEST_SERVICE_NAME, Constants.DEFAULT_NAMESPACE_ID);
        when(serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, TEST_SERVICE_NAME)).thenReturn(service);
        instanceManager.update(request);
    }

    @Test
    public void testDeregister() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);

        Instance instance = instanceManager.deregister(request);
        Assert.assertNull(instance);

        request.setParameter("ephemeral", "false");
        request.setParameter("ip", IP1);
        request.setParameter("port", "1");
        request.setParameter("healthy", "false");
        request.setParameter("enabled", "false");
        request.setParameter(CommonParams.NAMESPACE_ID, TEST_NAMESPACE);
        Service service = new Service(TEST_SERVICE_NAME, Constants.DEFAULT_NAMESPACE_ID);
        when(serviceManager.getService(TEST_NAMESPACE, TEST_SERVICE_NAME)).thenReturn(service);

        instance = instanceManager.deregister(request);
        Assert.assertEquals(IP1, instance.getIp());
        Assert.assertEquals(1, instance.getPort());
        Assert.assertEquals(1, instance.getWeight(), 0.01);
        Assert.assertFalse(instance.isHealthy());
        Assert.assertFalse(instance.isEnabled());
        Assert.assertFalse(instance.isEphemeral());
        Assert.assertEquals("DEFAULT", instance.getApp());
        Assert.assertEquals(0, instance.getMetadata().size());
        Assert.assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, instance.getCluster().getName());
    }

    @InjectMocks
    private InstanceManager instanceManager;
}
