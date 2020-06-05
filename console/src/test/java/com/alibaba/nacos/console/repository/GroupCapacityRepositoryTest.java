package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.GroupCapacity;
import com.alibaba.nacos.config.server.modules.entity.QGroupCapacity;
import com.alibaba.nacos.config.server.modules.repository.GroupCapacityRepository;
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
public class GroupCapacityRepositoryTest extends BaseTest {

    @Autowired
    private GroupCapacityRepository groupCapacityRepository;

    private GroupCapacity groupCapacity;

    @Before
    public void before() {
        String data = readClassPath("test-data/group_capacity.json");
        groupCapacity = JacksonUtils.toObj(data, GroupCapacity.class);
    }

    @Test
    public void insertTest() {
        groupCapacityRepository.save(groupCapacity);
    }

    @Test
    public void deleteTest() {
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll();
        iterable.forEach(s -> groupCapacityRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        booleanBuilder.and(qGroupCapacity.groupId.eq(groupCapacity.getGroupId()));
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
