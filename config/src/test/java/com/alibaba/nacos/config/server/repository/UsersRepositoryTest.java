package com.alibaba.nacos.config.server.repository;

import com.alibaba.nacos.config.server.modules.entity.QUsers;
import com.alibaba.nacos.config.server.modules.entity.Users;
import com.alibaba.nacos.config.server.modules.repository.UsersRepository;
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
public class UsersRepositoryTest {

    @Autowired
    private UsersRepository usersRepository;

    @Test
    public void insertTest() {
        usersRepository.save(buildUsers());
    }

    @Test
    public void deleteTest() {
        Iterable<Users> iterable = usersRepository.findAll();
        iterable.forEach(s -> usersRepository.delete(s));
    }

    @Test
    public void findAllTest() {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QUsers qUsers = QUsers.users;
        booleanBuilder.and(qUsers.username.eq("test"));
        Iterable<Users> iterable = usersRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    public Users buildUsers() {
        Users users = new Users();
        users.setUsername("test");
        users.setPassword("test");
        users.setEnabled(0);
        return users;
    }

}
