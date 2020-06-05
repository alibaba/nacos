package com.alibaba.nacos.console.repository;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.QUsers;
import com.alibaba.nacos.config.server.modules.entity.Users;
import com.alibaba.nacos.config.server.modules.repository.UsersRepository;
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
public class UsersRepositoryTest extends BaseTest {

    @Autowired
    private UsersRepository usersRepository;

    private Users users;

    @Before
    public void before() {
        String data = readClassPath("test-data/users.json");
        users = JacksonUtils.toObj(data, Users.class);
    }

    @Test
    public void insertTest() {
        usersRepository.save(users);
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
        booleanBuilder.and(qUsers.username.eq(users.getUsername()));
        Iterable<Users> iterable = usersRepository.findAll(booleanBuilder);
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

}
