package com.alibaba.nacos.config.server.repository;

import java.util.ArrayList;
import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTag;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoTag;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigInfoTagRepositoryTest {

    @Autowired
    private ConfigInfoTagRepository configInfoTagRepository;

    @Test
    public void insertTest() {
        configInfoTagRepository.save(buildConfigInfoTag());
    }

    @Test
    public void deleteTest() {
        Iterable<ConfigInfoTag> infoTags = configInfoTagRepository.findAll();
        infoTags.forEach(s -> configInfoTagRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        booleanBuilder.and(qConfigInfoTag.appName.eq("1"));
        Iterable<ConfigInfoTag> iterable = configInfoTagRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }


    public ConfigInfoTag buildConfigInfoTag() {
        ConfigInfoTag configInfoTag = new ConfigInfoTag();
        configInfoTag.setDataId("userService");
        configInfoTag.setGroupId("1");
        configInfoTag.setTenantId("1");
        configInfoTag.setTagId("1");
        configInfoTag.setAppName("1");
        configInfoTag.setContent("1");
        configInfoTag.setMd5("1");
        configInfoTag.setGmtCreate(new Date());
        configInfoTag.setGmtModified(new Date());
        configInfoTag.setSrcUser("1");
        configInfoTag.setSrcIp("1");
        return configInfoTag;
    }

}
