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

package com.alibaba.nacos.plugin.auth.impl.users;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

/**
 * Custom user service, implemented by directly access to database.
 *
 * @author wfnuser
 * @author nkorange
 */
public class NacosUserServiceDirectImpl extends AbstractCachedUserService implements NacosUserService {
    
    private final UserPersistService userPersistService;
    
    private final AuthConfigs authConfigs;
    
    public NacosUserServiceDirectImpl(AuthConfigs authConfigs, UserPersistService userPersistService) {
        super();
        this.userPersistService = userPersistService;
        this.authConfigs = authConfigs;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = getCachedUserMap().get(username);
        if (!authConfigs.isCachingEnabled()) {
            user = getUser(username);
        }
        if (user == null) {
            throw new UsernameNotFoundException(String.format("User %s not found", username));
        }
        return new NacosUserDetails(user);
    }
    
    @Override
    public void updateUserPassword(String username, String password) {
        userPersistService.updateUserPassword(username, PasswordEncoderUtil.encode(password));
    }
    
    @Override
    public Page<User> getUsers(int pageNo, int pageSize, String username) {
        return userPersistService.getUsers(pageNo, pageSize, username);
    }
    
    @Override
    public User getUser(String username) {
        return userPersistService.findUserByUsername(username);
    }
    
    @Override
    public List<String> findUserNames(String username) {
        return userPersistService.findUserLikeUsername(username);
    }
    
    @Override
    public void createUser(String username, String password, boolean encode) {
        validateUserCredentials(username, password);
        if (encode) {
            password = PasswordEncoderUtil.encode(password);
        }
        userPersistService.createUser(username, password);
    }
    
    @Override
    public void deleteUser(String username) {
        userPersistService.deleteUser(username);
    }
    
    @Override
    public Page<User> findUsers(String username, int pageNo, int pageSize) {
        return userPersistService.findUsersLike4Page(username, pageNo, pageSize);
    }
}
