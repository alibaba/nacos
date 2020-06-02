package com.alibaba.nacos.config.server.repository;

import java.sql.Timestamp;
import java.util.ArrayList;

import com.alibaba.nacos.config.server.modules.entity.QTenantCapacity;
import com.alibaba.nacos.config.server.modules.entity.TenantCapacity;
import com.alibaba.nacos.config.server.modules.repository.TenantCapacityRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TenantCapacityRepositoryTest {

    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;

    @Test
    public void insertTest() {
        tenantCapacityRepository.save(buildTenantCapacity());
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
        booleanBuilder.and(qTenantCapacity.tenantId.eq("test"));
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    public TenantCapacity buildTenantCapacity() {
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenantId("test");
        tenantCapacity.setQuota(0);
        tenantCapacity.setUsage(0);
        tenantCapacity.setMaxSize(0);
        tenantCapacity.setMaxAggrCount(0);
        tenantCapacity.setMaxAggrSize(0);
        tenantCapacity.setMaxHistoryCount(1);
        tenantCapacity.setGmtCreate(new Timestamp(new java.util.Date().getTime()));
        tenantCapacity.setGmtModified(new Timestamp(new java.util.Date().getTime()));
        return tenantCapacity;
    }

}
