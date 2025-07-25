/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos abstract cached user service.
 *
 * @author xiweng.yy
 */
public abstract class AbstractCachedUserService implements NacosUserService {
    
    private Map<String, User> userMap = new ConcurrentHashMap<>();
    
    protected AbstractCachedUserService() {
    }
    
    protected Map<String, User> getCachedUserMap() {
        return userMap;
    }
    
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    protected void reload() {
        try {
            Page<User> users = getUsers(1, Integer.MAX_VALUE, StringUtils.EMPTY);
            if (users == null) {
                return;
            }
            
            Map<String, User> map = new ConcurrentHashMap<>(16);
            for (User user : users.getPageItems()) {
                map.put(user.getUsername(), user);
            }
            userMap = map;
        } catch (Exception e) {
            Loggers.AUTH.warn("[LOAD-USERS] load failed", e);
        }
    }

    /**
     * [ISSUE #13625] check username and password is blank.
     */
    protected void validateUserCredentials(String username, String password) {
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username is blank");
        }
        if (StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("password is blank");
        }
    }
}
