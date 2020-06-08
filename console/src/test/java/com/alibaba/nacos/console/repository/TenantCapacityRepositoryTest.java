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
package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.QTenantCapacity;
import com.alibaba.nacos.config.server.modules.entity.TenantCapacity;
import com.alibaba.nacos.config.server.modules.repository.TenantCapacityRepository;
import com.alibaba.nacos.console.BaseTest;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Timestamp;
import java.util.ArrayList;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TenantCapacityRepositoryTest extends BaseTest {

    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;

    private TenantCapacity tenantCapacity;

    @Before
    public void before() {
        String data = readClassPath("test-data/tenant_capacity.json");
        tenantCapacity = JacksonUtils.toObj(data, TenantCapacity.class);
    }

    @Test
    public void insertTest() {
        tenantCapacityRepository.save(tenantCapacity);
    }

    @Test
    public void deleteTest() {
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll();
        iterable.forEach(s -> tenantCapacityRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        booleanBuilder.and(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId()));
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
