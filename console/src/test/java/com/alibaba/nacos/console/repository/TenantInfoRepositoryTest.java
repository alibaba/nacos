package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.QTenantInfo;
import com.alibaba.nacos.config.server.modules.entity.TenantInfo;
import com.alibaba.nacos.config.server.modules.repository.TenantInfoRepository;
import com.alibaba.nacos.console.BaseTest;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TenantInfoRepositoryTest extends BaseTest {

    @Autowired
    private TenantInfoRepository tenantInfoRepository;

    private TenantInfo tenantInfo;

    @Before
    public void before() {
        String data = readClassPath("test-data/tenant_info.json");
        tenantInfo = JacksonUtils.toObj(data, TenantInfo.class);
    }

    @Test
    public void insertTest() {
        tenantInfoRepository.save(tenantInfo);
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
        booleanBuilder.and(qTenantInfo.tenantId.eq(tenantInfo.getTenantId()));
        Iterable<TenantInfo> iterable = tenantInfoRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }


}
