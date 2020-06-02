package com.alibaba.nacos.config.server.repository;

import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggr;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoAggr;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoAggrRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoAggrRepositoryTest {

    @Autowired
    private ConfigInfoAggrRepository configInfoAggrRepository;

    @Test
    public void insertTest() {
        configInfoAggrRepository.save(buildConfigInfoAggr());
    }

    @Test
    public void delete() {
        Iterable<ConfigInfoAggr> infoAggrs = configInfoAggrRepository.findAll();
        infoAggrs.forEach(s -> configInfoAggrRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        booleanBuilder.and(qConfigInfoAggr.appName.eq("1"));
        booleanBuilder.and(qConfigInfoAggr.dataId.eq("userService"));
        Iterable<ConfigInfoAggr> infoAggrs = configInfoAggrRepository.findAll(booleanBuilder);
        Assert.assertNotNull(infoAggrs);
    }


    private ConfigInfoAggr buildConfigInfoAggr() {
        ConfigInfoAggr configInfoAggr = new ConfigInfoAggr();
        configInfoAggr.setGmtModified(new Date());
        configInfoAggr.setDataId("userService");
        configInfoAggr.setGroupId("DEFAULT_GROUP");
        configInfoAggr.setDatumId("1");
        configInfoAggr.setContent("1");
        configInfoAggr.setAppName("1");
        configInfoAggr.setTenantId("1");
        return configInfoAggr;
    }

}
