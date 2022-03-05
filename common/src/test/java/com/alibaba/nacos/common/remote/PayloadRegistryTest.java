package com.alibaba.nacos.common.remote;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class PayloadRegistryTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
    }

    @Test
    public void testInit() {
        PayloadRegistry.init();
        Assert.assertNotNull(PayloadRegistry.getClassByType("NotifySubscriberResponse"));
        Assert.assertNotNull(PayloadRegistry.getClassByType("InstanceRequest"));
    }
}