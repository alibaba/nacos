package com.alibaba.nacos.console.service;

import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.service.capacity.CapacityServiceTmp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author zhangshun
 * @version $Id: CapacityServiceTest.java,v 0.1 2020年06月06日 16:12 $Exp
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class CapacityServiceTest {

    @Autowired
    private CapacityServiceTmp capacityServiceTmp;


    @Test
    public void correctGroupUsageTest() {
        capacityServiceTmp.correctGroupUsage("");
    }

    @Test
    public void correctTenantUsageTest() {
        capacityServiceTmp.correctTenantUsage("");
    }

    @Test
    public void insertAndUpdateClusterUsageTest() {
        capacityServiceTmp.insertAndUpdateClusterUsage(CounterMode.INCREMENT, true);
    }

    @Test
    public void updateClusterUsageTest() {
        capacityServiceTmp.updateClusterUsage(CounterMode.INCREMENT);
    }

    @Test
    public void insertAndUpdateGroupUsageTest() {
        capacityServiceTmp.insertAndUpdateGroupUsage(CounterMode.INCREMENT, "", true);
    }

    @Test
    public void getGroupCapacityTest() {
        capacityServiceTmp.getGroupCapacity("");
    }

    @Test
    public void updateGroupUsageTest() {
        capacityServiceTmp.updateGroupUsage(CounterMode.INCREMENT, "");
    }

    @Test
    public void getCapacityWithDefaultTest() {
        capacityServiceTmp.getCapacityWithDefault("", "");
    }

    @Test
    public void initCapacityTest() {
        capacityServiceTmp.initCapacity("","");
    }

    @Test
    public void initGroupCapacity() {
        capacityServiceTmp.initGroupCapacity("");
    }

    @Test
    public void getCapacityTest() {
        capacityServiceTmp.getCapacity("","");
    }

    @Test
    public void insertAndUpdateTenantUsageTest() {
        capacityServiceTmp.insertAndUpdateTenantUsage(CounterMode.INCREMENT,"",true);
    }

    @Test
    public void updateTenantUsageTest() {
        capacityServiceTmp.updateTenantUsage(CounterMode.INCREMENT,"");
    }

    @Test
    public void initTenantCapacityTest() {
        capacityServiceTmp.initTenantCapacity("");
    }

    @Test
    public void initTenantCapacity1Test() {
        capacityServiceTmp.initTenantCapacity("",10,10,10,10);
    }

    @Test
    public void getTenantCapacityTest() {
        capacityServiceTmp.getTenantCapacity("");
    }

    @Test
    public void insertOrUpdateCapacityTest() {
        capacityServiceTmp.insertOrUpdateCapacity("","",10,10,10,10);
    }

}
