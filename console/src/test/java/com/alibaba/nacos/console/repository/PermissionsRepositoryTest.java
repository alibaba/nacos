package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.Permissions;
import com.alibaba.nacos.config.server.modules.entity.QPermissions;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
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
public class PermissionsRepositoryTest extends BaseTest {

    @Autowired
    private PermissionsRepository permissionsRepository;

    private Permissions permissions;

    @Before
    public void before() {
        String data = readClassPath("test-data/permissions.json");
        permissions = JacksonUtils.toObj(data, Permissions.class);
    }

    @Test
    public void insertTest() {
        permissionsRepository.save(permissions);
    }

    @Test
    public void delete() {
        Iterable<Permissions> iterable = permissionsRepository.findAll();
        iterable.forEach(s -> permissionsRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QPermissions qPermissions = QPermissions.permissions;
        booleanBuilder.and(qPermissions.resource.eq(permissions.getResource()));
        Iterable<Permissions> iterable = permissionsRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
