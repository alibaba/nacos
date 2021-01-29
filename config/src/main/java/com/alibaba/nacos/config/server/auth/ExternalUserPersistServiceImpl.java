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

import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.modules.entity.QUsersEntity;
import com.alibaba.nacos.config.server.modules.entity.UsersEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.UserMapStruct;
import com.alibaba.nacos.config.server.modules.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Implemetation of ExternalUserPersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalUserPersistServiceImpl implements UserPersistService {
    
    @Autowired
    private UsersRepository usersRepository;
    
    /**
     * Execute create user operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    public void createUser(String username, String password) {
        usersRepository.save(new UsersEntity(username, password, 1));
    }
    
    /**
     * Execute delete user operation.
     *
     * @param username username string value.
     */
    public void deleteUser(String username) {
        usersRepository.findOne(QUsersEntity.usersEntity.username.eq(username))
                .ifPresent(u -> usersRepository.delete(u));
    }
    
    /**
     * Execute update user password operation.
     *
     * @param username username string value.
     * @param password password string value.
     */
    public void updateUserPassword(String username, String password) {
        usersRepository.findOne(QUsersEntity.usersEntity.username.eq(username)).ifPresent(u -> {
            u.setPassword(password);
            usersRepository.save(u);
        });
    }
    
    /**
     * Execute find user by username operation.
     *
     * @param username username string value.
     * @return User model.
     */
    public User findUserByUsername(String username) {
        UsersEntity usersEntity = usersRepository.findOne(QUsersEntity.usersEntity.username.eq(username))
                .orElse(null);
        return UserMapStruct.INSTANCE.convertUser(usersEntity);
    }
    
    public Page<User> getUsers(int pageNo, int pageSize) {
        org.springframework.data.domain.Page<UsersEntity> sPage = usersRepository
                .findAll(PageRequest.of(pageNo - 1, pageSize));
        Page<User> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(UserMapStruct.INSTANCE.convertUserList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public List<String> findUserLikeUsername(String username) {
        List<UsersEntity> usersEntities = (List<UsersEntity>) usersRepository
                .findAll(QUsersEntity.usersEntity.username.like(username));
        return usersEntities.stream().map(UsersEntity::getUsername).collect(Collectors.toList());
    }
}
