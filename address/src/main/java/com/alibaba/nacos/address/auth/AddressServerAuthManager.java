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

package com.alibaba.nacos.address.auth;

import com.alibaba.nacos.auth.AuthManager;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.model.User;

/**
 * Address server auth manager.
 *
 * <p>For #3091, Only implement an empty auth manager so that address server can startup.</p>
 *
 * @author xiweng.yy
 */
public class AddressServerAuthManager implements AuthManager {
    
    @Override
    public User login(Object request) throws AccessException {
        User result = new User();
        result.setUserName("nacos");
        return result;
    }
    
    @Override
    public User loginRemote(Object request) throws AccessException {
        return null;
    }
    
    @Override
    public void auth(Permission permission, User user) throws AccessException {
    }
}
