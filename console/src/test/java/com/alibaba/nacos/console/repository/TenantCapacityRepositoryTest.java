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
