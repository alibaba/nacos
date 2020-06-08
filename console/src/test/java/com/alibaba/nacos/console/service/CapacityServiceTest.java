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
package com.alibaba.nacos.console.service;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.modules.entity.Capacity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.TenantCapacity;
import com.alibaba.nacos.config.server.service.capacity.CapacityServiceTmp;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
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
public class CapacityServiceTest extends BaseTest {

    @Autowired
    private CapacityServiceTmp capacityServiceTmp;

    private ConfigInfo configInfo;

    @Before
    public void before() {
        String data = readClassPath("test-data/config_info.json");
        configInfo = JacksonUtils.toObj(data, ConfigInfo.class);
    }

    @Test
    public void correctGroupUsageTest() {
        capacityServiceTmp.correctGroupUsage(configInfo.getGroupId());
    }

    @Test
    public void correctTenantUsageTest() {
        capacityServiceTmp.correctTenantUsage(configInfo.getTenantId());
    }

    @Test
    public void insertAndUpdateClusterUsageTest() {
        boolean result = capacityServiceTmp.insertAndUpdateClusterUsage(CounterMode.INCREMENT, true);
        Assert.assertTrue(result);
    }

    @Test
    public void updateClusterUsageTest() {
        capacityServiceTmp.updateClusterUsage(CounterMode.INCREMENT);
    }

    @Test
    public void insertAndUpdateGroupUsageTest() {
        capacityServiceTmp.insertAndUpdateGroupUsage(CounterMode.DECREMENT, "", false);
    }

    @Test
    public void getGroupCapacityTest() {
        capacityServiceTmp.getGroupCapacity("");
    }
    //

    @Test
    public void updateGroupUsageTest() {
        capacityServiceTmp.updateGroupUsage(CounterMode.INCREMENT, "");
    }

    @Test
    public void initTenantCapacity1Test() {
        capacityServiceTmp.initTenantCapacity("testTenant", null, 10, 10, 10);
    }


    @Test
    public void getCapacityWithDefaultTest() {
        Capacity capacity = capacityServiceTmp.getCapacityWithDefault("", "testTenant2");
        Assert.assertNotNull(capacity);
    }


    @Test
    public void initCapacityTest() {
        capacityServiceTmp.initCapacity("testGroup1", "");
    }


    //
    @Test
    public void getCapacityTest() {
        capacityServiceTmp.getCapacity("", "");
    }

    @Test
    public void insertAndUpdateTenantUsageTest() {
        capacityServiceTmp.insertAndUpdateTenantUsage(CounterMode.INCREMENT, "", true);
    }

    @Test
    public void updateTenantUsageTest() {
        capacityServiceTmp.updateTenantUsage(CounterMode.INCREMENT, "");
    }

    @Test
    public void initTenantCapacityTest() {
        capacityServiceTmp.initTenantCapacity("testTenant1");
    }


    @Test
    public void getTenantCapacityTest() {
        TenantCapacity tenantCapacity = capacityServiceTmp.getTenantCapacity("");
        Assert.assertNotNull(tenantCapacity);
    }

    @Test
    public void insertOrUpdateCapacityTest() {
        boolean result = capacityServiceTmp.insertOrUpdateCapacity("", "", 10, 10, 10, 10);
        Assert.assertTrue(result);
    }

    @Test
    public void initGroupCapacityTest() {
        boolean result = capacityServiceTmp.initGroupCapacity("testGroup");
        Assert.assertTrue(result);
    }

}
