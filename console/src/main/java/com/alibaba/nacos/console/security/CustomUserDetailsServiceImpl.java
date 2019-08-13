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
package com.alibaba.nacos.console.security;


import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.config.server.service.PersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custem user service
 *
 * @author wfnuser
 */
@Service
public class CustomUserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private transient PersistService persistService;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        User user = persistService.findUserByUsername(userName);
        if (user == null) {
            throw new UsernameNotFoundException(userName);
        }
        return new CustomUserDetails(user);
    }

    public void updateUserPassword(String username, String password) throws Exception {
        persistService.updateUserPassword(username, password);
    }
}
