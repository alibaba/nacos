package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.modules.entity.QUsers;
import com.alibaba.nacos.config.server.modules.entity.Users;
import com.alibaba.nacos.config.server.modules.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserPersistServiceTmp {

    @Autowired
    private UsersRepository usersRepository;

    public void createUser(String username, String password) {
        usersRepository.save(new Users(username, password, 1));
    }

    public void deleteUser(String username) {
        usersRepository.findOne(QUsers.users.password.eq(username))
            .ifPresent(u -> usersRepository.delete(u));
    }

    public void updateUserPassword(String username, String password) {
        usersRepository.findOne(QUsers.users.username.eq(username))
            .ifPresent(u -> {
                u.setPassword(password);
                usersRepository.save(u);
            });
    }

    public Users findUserByUsername(String username) {
        return usersRepository.findOne(QUsers.users.username.eq(username))
            .orElseThrow(() -> new RuntimeException(username + " not exist"));
    }

    public Page<Users> getUsers(int pageNo, int pageSize) {
        return usersRepository.findAll(null, PageRequest.of(pageNo, pageSize));
    }

}
