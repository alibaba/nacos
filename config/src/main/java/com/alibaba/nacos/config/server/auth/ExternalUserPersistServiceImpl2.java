/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.modules.entity.QUsers;
import com.alibaba.nacos.config.server.modules.entity.UsersEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigHistoryInfoMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.UserMapStruct;
import com.alibaba.nacos.config.server.modules.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * @author Nacos
 */
@Slf4j
@Service
public class ExternalUserPersistServiceImpl2 {

    @Autowired
    private UsersRepository usersRepository;

    public void createUser(String username, String password) {
        usersRepository.save(new UsersEntity(username, password, 1));
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

    public User findUserByUsername(String username) {
        UsersEntity usersEntity = usersRepository.findOne(QUsers.users.username.eq(username))
            .orElseThrow(() -> new RuntimeException(username + " not exist"));
        return UserMapStruct.INSTANCE.convertUser(usersEntity);
    }

    public Page<User> getUsers(int pageNo, int pageSize) {
        org.springframework.data.domain.Page<UsersEntity> sPage =
            usersRepository.findAll(null, PageRequest.of(pageNo, pageSize));
        Page<User> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(UserMapStruct.INSTANCE.convertUserList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

}
