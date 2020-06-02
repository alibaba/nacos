package com.alibaba.nacos.config.server.repository;

import java.util.ArrayList;
import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.QTenantInfo;
import com.alibaba.nacos.config.server.modules.entity.TenantInfo;
import com.alibaba.nacos.config.server.modules.repository.TenantInfoRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TenantInfoRepositoryTest {

    @Autowired
    private TenantInfoRepository tenantInfoRepository;

    @Test
    public void insertTest() {
        tenantInfoRepository.save(buildTenantInfo());
    }

    @Test
    public void deleteTest() {
        Iterable<TenantInfo> iterable = tenantInfoRepository.findAll();
        iterable.forEach(s -> tenantInfoRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QTenantInfo qTenantInfo = QTenantInfo.tenantInfo;
        booleanBuilder.and(qTenantInfo.tenantId.eq("1"));
        Iterable<TenantInfo> iterable = tenantInfoRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    public TenantInfo buildTenantInfo() {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setKp("1");
        tenantInfo.setTenantId("1");
        tenantInfo.setTenantName("1");
        tenantInfo.setTenantDesc("1");
        tenantInfo.setCreateSource("1");
        tenantInfo.setGmtCreate(new Date().getTime());
        tenantInfo.setGmtModified(new Date().getTime());
        return tenantInfo;
    }

}
