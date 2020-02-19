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
package com.alibaba.nacos.console.security.nacos.users;


import com.alibaba.nacos.config.server.auth.UserPersistService;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Custem user service
 *
 * @author wfnuser
 * @author nkorange
 */
@Service
public class NacosUserDetailsServiceImpl implements UserDetailsService {

    private Cache<String, User> userMap = CacheBuilder.newBuilder()
            .maximumSize(Integer.MAX_VALUE)
            .expireAfterWrite(15000, TimeUnit.MILLISECONDS)
            .build();

    @Autowired
    private UserPersistService userPersistService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = null;
        try {
            user = userMap.get(username, () -> userPersistService.findUserByUsername(username));
            if (user == null) {
                throw new UsernameNotFoundException(username);
            }
            return new NacosUserDetails(user);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateUserPassword(String username, String password) {
        userPersistService.updateUserPassword(username, password);
        userMap.invalidate(username);
    }

    public Page<User> getUsersFromDatabase(int pageNo, int pageSize) {
        return userPersistService.getUsers(pageNo, pageSize);
    }

    public User getUser(String username) {
        try {
            return userMap.get(username, () -> userPersistService.findUserByUsername(username));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public User getUserFromDatabase(String username) {
        return userPersistService.findUserByUsername(username);
    }

    public void createUser(String username, String password) {
        userPersistService.createUser(username, password);
    }

    public void deleteUser(String username) {
        userPersistService.deleteUser(username);
        userMap.invalidate(username);
    }
}
