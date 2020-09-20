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

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;

import java.util.List;

/**
 * User CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface UserPersistService {
    
    void createUser(String username, String password);
    
    void deleteUser(String username);
    
    void updateUserPassword(String username, String password);
    
    User findUserByUsername(String username);
    
    Page<User> getUsers(int pageNo, int pageSize);

    List<String> findUserLikeUsername(String username);

}
