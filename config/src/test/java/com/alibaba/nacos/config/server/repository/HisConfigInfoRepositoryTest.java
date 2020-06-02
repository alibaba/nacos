package com.alibaba.nacos.config.server.repository;

import java.util.ArrayList;
import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.HisConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.QHisConfigInfo;
import com.alibaba.nacos.config.server.modules.repository.HisConfigInfoRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class HisConfigInfoRepositoryTest {

    @Autowired
    private HisConfigInfoRepository hisConfigInfoRepository;

    @Test
    public void insertTest() {
        hisConfigInfoRepository.save(buildHisConfigInfo());
    }

    @Test
    public void deleteTest() {
        Iterable<HisConfigInfo> iterable = hisConfigInfoRepository.findAll();
        iterable.forEach(s -> hisConfigInfoRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        booleanBuilder.and(qHisConfigInfo.tenantId.eq("1"));
        Iterable<HisConfigInfo> iterable = hisConfigInfoRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    public HisConfigInfo buildHisConfigInfo() {
        HisConfigInfo hisConfigInfo = new HisConfigInfo();
        hisConfigInfo.setId(1l);
        hisConfigInfo.setDataId("1");
        hisConfigInfo.setGroupId("1");
        hisConfigInfo.setAppName("1");
        hisConfigInfo.setContent("1");
        hisConfigInfo.setMd5("1");
        hisConfigInfo.setGmtCreate(new Date());
        hisConfigInfo.setGmtModified(new Date());
        hisConfigInfo.setSrcUser("1");
        hisConfigInfo.setSrcIp("1");
        hisConfigInfo.setOpType("1");
        hisConfigInfo.setTenantId("1");
        return hisConfigInfo;
    }

}
