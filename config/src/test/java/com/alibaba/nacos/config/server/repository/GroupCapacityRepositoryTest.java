package com.alibaba.nacos.config.server.repository;

import java.util.ArrayList;
import java.util.Date;

import com.alibaba.nacos.config.server.modules.entity.GroupCapacity;
import com.alibaba.nacos.config.server.modules.entity.QGroupCapacity;
import com.alibaba.nacos.config.server.modules.repository.GroupCapacityRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GroupCapacityRepositoryTest {

    @Autowired
    private GroupCapacityRepository groupCapacityRepository;

    @Test
    public void insertTest() {
        groupCapacityRepository.save(buildGroupCapacity());
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
        booleanBuilder.and(qGroupCapacity.groupId.eq("test1"));
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }


    public GroupCapacity buildGroupCapacity() {
        GroupCapacity capacity = new GroupCapacity();
        capacity.setGroupId("test1");
        capacity.setQuota(1);
        capacity.setUsage(1);
        capacity.setMaxSize(1);
        capacity.setMaxAggrCount(1);
        capacity.setMaxAggrSize(1);
        capacity.setMaxHistoryCount(1);
        capacity.setGmtCreate(new Date());
        capacity.setGmtModified(new Date());
        return capacity;
    }

}
