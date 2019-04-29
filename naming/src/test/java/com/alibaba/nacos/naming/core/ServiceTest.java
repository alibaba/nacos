package com.alibaba.nacos.naming.core;

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
    private Service service;

    @Before
    public void before() {
        super.before();
    }

    @Test
    public void testUpdateIPs() {
        service.setName("test-service");
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance("1.1.1.1", 1, "test-instance1");
        instances.add(instance);
        service.updateIPs(instances, true);
        Assert.assertEquals(instances, service.allIPs(true));

        instances = new ArrayList<>();
        instance = new Instance();
        instance.setIp("2.2.2.2");
        instance.setPort(2);
        instances.add(instance);
        instances.add(null);
        service.updateIPs(instances, true);
        instances.remove(null);
        Assert.assertEquals(instances, service.allIPs(true));
    }
}
