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

import java.util.Set;

/**
 * Nacos User.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class NacosUser extends User {
    
    private String token;
    
    private boolean globalAdmin = false;

    private Set<String> roles;

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public boolean isGlobalAdmin() {
        return globalAdmin;
    }
    
    public void setGlobalAdmin(boolean globalAdmin) {
        this.globalAdmin = globalAdmin;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NacosUser{");
        sb.append("token='").append(token).append('\'');
        sb.append(", globalAdmin=").append(globalAdmin);
        sb.append(", roles=").append(roles);
        sb.append('}');
        return sb.toString();
    }
}
