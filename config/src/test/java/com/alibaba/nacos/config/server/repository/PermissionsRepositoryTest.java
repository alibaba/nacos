package com.alibaba.nacos.config.server.repository;

import com.alibaba.nacos.config.server.modules.entity.Permissions;
import com.alibaba.nacos.config.server.modules.entity.QPermissions;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
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
public class PermissionsRepositoryTest {

    @Autowired
    private PermissionsRepository permissionsRepository;

    @Test
    public void insertTest() {
        permissionsRepository.save(buildPermissions());
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
        booleanBuilder.and(qPermissions.resource.eq("test"));
        Iterable<Permissions> iterable = permissionsRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    public Permissions buildPermissions() {
        Permissions permissions = new Permissions();
        permissions.setRole("test");
        permissions.setResource("test");
        permissions.setAction("test");
        return permissions;
    }

}
