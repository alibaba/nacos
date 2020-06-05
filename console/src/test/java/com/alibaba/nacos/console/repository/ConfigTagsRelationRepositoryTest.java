package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.ConfigTagsRelation;
import com.alibaba.nacos.config.server.modules.entity.QConfigTagsRelation;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
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


@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigTagsRelationRepositoryTest extends BaseTest {

    @Autowired
    private ConfigTagsRelationRepository configTagsRelationRepository;

    private ConfigTagsRelation configTagsRelation;

    @Before
    public void before() {
        String data = readClassPath("test-data/config_tags_relation.json");
        configTagsRelation = JacksonUtils.toObj(data, ConfigTagsRelation.class);
    }

    @Test
    public void insertTest() {
        configTagsRelationRepository.save(configTagsRelation);
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
        booleanBuilder.and(qConfigTagsRelation.tagName.eq(configTagsRelation.getTagName()));
        Iterable<ConfigTagsRelation> iterable = configTagsRelationRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
