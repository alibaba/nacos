package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.modules.entity.Capacity;
import com.alibaba.nacos.config.server.modules.entity.TenantCapacity;
import com.alibaba.nacos.config.server.service.capacity.CapacityServiceTmp;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CapacityServiceTest {

    @Autowired
    private CapacityServiceTmp capacityService;


    @Test
    public void getTenantCapacityTest() {
        TenantCapacity tenantCapacity = capacityService.getTenantCapacity("test");
        Assert.assertNotNull(tenantCapacity);
    }

    @Test
    public void getCapacityTest() {
       Capacity capacity = capacityService.getCapacity("test1",null);
       Assert.assertNotNull(capacity);
    }


}
