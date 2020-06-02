package com.alibaba.nacos.config.server.repository;

import java.util.ArrayList;
import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBeta;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoBeta;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoBetaRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoBetaRepositoryTest {

    @Autowired
    private ConfigInfoBetaRepository configInfoBetaRepository;

    @Test
    public void insertTest() {
        configInfoBetaRepository.save(buildConfigInfoBeta());
    }

    @Test
    public void deleteTest() {
        Iterable<ConfigInfoBeta> iterable = configInfoBetaRepository.findAll();
        iterable.forEach(s -> configInfoBetaRepository.delete(s));

    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        booleanBuilder.and(qConfigInfoBeta.tenantId.eq("1"));
        Iterable<ConfigInfoBeta> infoBetas = configInfoBetaRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) infoBetas).size() > 0);
    }


    public ConfigInfoBeta buildConfigInfoBeta() {
        ConfigInfoBeta configInfoBeta = new ConfigInfoBeta();
        configInfoBeta.setDataId("userService");
        configInfoBeta.setGroupId("1");
        configInfoBeta.setAppName("1");
        configInfoBeta.setContent("1");
        configInfoBeta.setBetaIps("1");
        configInfoBeta.setMd5("1");
        configInfoBeta.setGmtCreate(new Date());
        configInfoBeta.setGmtModified(new Date());
        configInfoBeta.setSrcUser("1");
        configInfoBeta.setSrcIp("1");
        configInfoBeta.setTenantId("1");
        return configInfoBeta;
    }

}
