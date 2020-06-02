package com.alibaba.nacos.config.server.repository;

import com.alibaba.nacos.config.server.modules.entity.ConfigTagsRelation;
import com.alibaba.nacos.config.server.modules.entity.QConfigTagsRelation;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigTagsRelationRepositoryTest {

    @Autowired
    private ConfigTagsRelationRepository configTagsRelationRepository;

    @Test
    public void insertTest() {
        configTagsRelationRepository.save(buildConfigTagsRelation());
    }

    @Test
    public void deleteTest() {
        Iterable<ConfigTagsRelation> iterable = configTagsRelationRepository.findAll();
        iterable.forEach(s -> configTagsRelationRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigTagsRelation qConfigTagsRelation = QConfigTagsRelation.configTagsRelation;
        booleanBuilder.and(qConfigTagsRelation.tagName.eq("test"));
        Iterable<ConfigTagsRelation> iterable = configTagsRelationRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }


    public ConfigTagsRelation buildConfigTagsRelation() {
        ConfigTagsRelation configTagsRelation = new ConfigTagsRelation();
        configTagsRelation.setId(0L);
        configTagsRelation.setTagName("test");
        configTagsRelation.setTagType("test");
        configTagsRelation.setDataId("test");
        configTagsRelation.setGroupId("test");
        configTagsRelation.setTenantId("test");
        return configTagsRelation;
    }

}
