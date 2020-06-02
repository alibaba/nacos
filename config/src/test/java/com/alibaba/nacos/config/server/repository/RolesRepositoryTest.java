package com.alibaba.nacos.config.server.repository;

import com.alibaba.nacos.config.server.modules.entity.QRoles;
import com.alibaba.nacos.config.server.modules.entity.Roles;
import com.alibaba.nacos.config.server.modules.repository.RolesRepository;
import com.querydsl.core.BooleanBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RolesRepositoryTest {

    @Autowired
    private RolesRepository rolesRepository;

    @Test
    public void insertTest() {
        rolesRepository.save(buildRoles());
    }

    @Test
    public void deleteTest() {
        Iterable<Roles> iterable = rolesRepository.findAll();
        iterable.forEach(s -> rolesRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QRoles qRoles = QRoles.roles;
        booleanBuilder.and(qRoles.username.eq("test"));
        Iterable<Roles> iterable = rolesRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }


    public Roles buildRoles() {
        Roles roles = new Roles();
        roles.setUsername("test");
        roles.setRole("test");
        return roles;
    }

}
