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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jifengnan  2019-04-28
 */
public class ServiceTest extends BaseTest {
    @Spy
    private Service service = new Service(TEST_SERVICE_NAME);

    @Before
    public void before() {
        super.before();
    }

    @Test
    public void testUpdateIPs() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = JSON.parseObject(InstanceTest.OLD_DATA, Instance.class);
        instances.add(instance);
        service.updateIPs(instances, true);
        Assert.assertEquals(instances, service.allIPs(true));

        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, service);
        instances = new ArrayList<>();
        instance = new Instance(IP2, 2, cluster);
        instances.add(instance);
        instances.add(null);
        service.updateIPs(instances, true);
        instances.remove(null);
        Assert.assertEquals(instances, service.allIPs(true));
        Assert.assertEquals(cluster, service.getClusterMap().get(TEST_CLUSTER_NAME));
    }

    @Test
    public void testCreateService() {
        Service service = new Service(TEST_SERVICE_NAME, TEST_NAMESPACE);
        Assert.assertEquals(TEST_SERVICE_NAME, service.getName());
        Assert.assertEquals(TEST_NAMESPACE, service.getNamespaceId());

        service = new Service(TEST_SERVICE_NAME, TEST_NAMESPACE, TEST_GROUP_NAME);
        Assert.assertEquals(TEST_SERVICE_NAME, service.getName());
        Assert.assertEquals(TEST_NAMESPACE, service.getNamespaceId());
        Assert.assertEquals(TEST_GROUP_NAME, service.getGroupName());
    }

    @Test
    public void testCreateService_NullName() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("service name can and only can have these characters: 0-9a-zA-Z-._:, current: null");
        new Service(null, TEST_NAMESPACE, TEST_GROUP_NAME);
    }

    @Test
    public void testCreateService_BlankName() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("service name can and only can have these characters: 0-9a-zA-Z-._:, current: null");
        new Service(" ", TEST_NAMESPACE, TEST_GROUP_NAME);
    }

    @Test
    public void testCreateService_NullNameSpace() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("namespaceId must not be null and must contain at least one non-whitespace character");
        new Service(TEST_SERVICE_NAME, null, TEST_GROUP_NAME);
    }

    @Test
    public void testCreateService_BlankNameSpace() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("namespaceId must not be null and must contain at least one non-whitespace character");
        new Service(TEST_SERVICE_NAME, " ", TEST_GROUP_NAME);
    }

    @Test
    public void testCreateService_NullGroupName() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("group name must not be null and must contain at least one non-whitespace character");
        new Service(TEST_SERVICE_NAME, TEST_NAMESPACE, null);
    }

    @Test
    public void testCreateService_BlankGroupName() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("group name must not be null and must contain at least one non-whitespace character");
        new Service(TEST_SERVICE_NAME, TEST_NAMESPACE, " ");
    }
}
